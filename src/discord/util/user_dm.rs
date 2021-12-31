use crate::constants::EMBED_COLOR;
use chrono::{TimeZone, Utc};
use serenity::builder::CreateEmbed;
use serenity::http::Http;
use serenity::model::id::UserId;
use tracing::error;

pub enum ModActionKind {
    Ban { expiration_time: Option<u64> },
    Kick,
    Warn,
    Mute { expiration_time: Option<u64> },
    Softban,
    Hardban,
}

impl ModActionKind {
    fn title(&self, guild_name: &str) -> String {
        match self {
            ModActionKind::Ban { .. } => format!("Banned from {}", guild_name),
            ModActionKind::Kick => format!("Kicked from {}", guild_name),
            ModActionKind::Warn => format!("Warned in {}", guild_name),
            ModActionKind::Mute { .. } => format!("Muted in {}", guild_name),
            ModActionKind::Softban => format!("Softbanned from {}", guild_name),
            ModActionKind::Hardban => format!("Hardbanned from {}", guild_name),
        }
    }

    fn by_moderator(&self, mod_user_tag_and_id: &str) -> String {
        match self {
            ModActionKind::Ban { .. } => format!("Banned by {}", mod_user_tag_and_id),
            ModActionKind::Kick => format!("Kicked by {}", mod_user_tag_and_id),
            ModActionKind::Warn => format!("Warned by {}", mod_user_tag_and_id),
            ModActionKind::Mute { .. } => format!("Muted by {}", mod_user_tag_and_id),
            ModActionKind::Softban => format!("Softbanned by {}", mod_user_tag_and_id),
            ModActionKind::Hardban => format!("Hardbanned by {}", mod_user_tag_and_id),
        }
    }

    fn create_expiration_date_field(&self, embed: &mut CreateEmbed) {
        match self {
            ModActionKind::Ban { expiration_time } => {
                let value = expiration_time
                    .map(|time| format!("<t:{}>", time))
                    .unwrap_or_else(|| "Indefinitely".into());
                embed.field("Banned until", value, false);
            }
            ModActionKind::Mute { expiration_time } => {
                let value = expiration_time
                    .map(|time| format!("<t:{}>", time))
                    .unwrap_or_else(|| "Indefinitely".into());
                embed.field("Muted until", value, false);
            }
            _ => {}
        }
    }
}

pub async fn notify_user_for_mod_action(
    http: &Http,
    user: UserId,
    kind: ModActionKind,
    reason: &str,
    action_time: u64,
    guild_name: &str,
    mod_user_tag_and_id: &str,
) {
    let dm_channel_result = user.create_dm_channel(http).await.map_err(|err| {
        error!("failed to create DM channel {}", err);
        err
    });

    if let Ok(channel) = dm_channel_result {
        let _ = channel
            .send_message(http, |message| {
                message.embed(|embed| {
                    embed
                        .title(kind.title(guild_name))
                        .colour(EMBED_COLOR)
                        .footer(|footer| footer.text(kind.by_moderator(mod_user_tag_and_id)))
                        .timestamp(Utc.timestamp(action_time as i64, 0).to_rfc3339())
                        .field("Reason", reason, false);

                    kind.create_expiration_date_field(embed);
                    embed
                })
            })
            .await
            .map_err(|err| {
                error!("failed to create DM {}", err);
                err
            });
    }
}
