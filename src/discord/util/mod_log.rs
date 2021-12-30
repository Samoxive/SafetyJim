use crate::discord::util::{SerenityErrorExt, UserExt};
use chrono::{TimeZone, Utc};
use serenity::builder::CreateEmbed;
use serenity::http::Http;
use serenity::model::id::ChannelId;
use serenity::model::misc::Mentionable;
use serenity::model::user::User;
use serenity::utils::Color;
use tracing::error;

pub enum ModLogAction {
    Ban { expiration_time: Option<u64> },
    Kick,
    Warn,
    Mute { expiration_time: Option<u64> },
    Softban,
    Hardban,
}

impl ModLogAction {
    fn color(&self) -> Color {
        match self {
            ModLogAction::Ban { .. } => Color::new(0xFF2900),
            ModLogAction::Kick => Color::new(0xFF9900),
            ModLogAction::Warn => Color::new(0xFFEB00),
            ModLogAction::Mute { .. } => Color::new(0xFFFFFF),
            ModLogAction::Softban => Color::new(0xFF55DD),
            ModLogAction::Hardban => Color::new(0x700000),
        }
    }

    fn name(&self) -> &'static str {
        match self {
            ModLogAction::Ban { .. } => "Ban",
            ModLogAction::Kick => "Kick",
            ModLogAction::Warn => "Warn",
            ModLogAction::Mute { .. } => "Mute",
            ModLogAction::Softban => "Softban",
            ModLogAction::Hardban => "Hardban",
        }
    }

    fn create_expiration_date_field(&self, embed: &mut CreateEmbed) {
        match self {
            ModLogAction::Ban { expiration_time } => {
                let value = expiration_time
                    .map(|time| format!("<t:{}>", time))
                    .unwrap_or_else(|| "Indefinitely".into());
                embed.field("Banned until", value, false);
            }
            ModLogAction::Mute { expiration_time } => {
                let value = expiration_time
                    .map(|time| format!("<t:{}>", time))
                    .unwrap_or_else(|| "Indefinitely".into());
                embed.field("Muted until", value, false);
            }
            _ => {}
        }
    }
}

pub enum CreateModLogEntryError {
    ModLogChannelDoesNotExist,
    Unauthorized,
    Unknown,
}

impl CreateModLogEntryError {
    pub fn to_interaction_response(&self) -> &'static str {
        match self {
            CreateModLogEntryError::ModLogChannelDoesNotExist => "Success. Failed to create moderator log entry because log channel no longer exists. You can change it at web dashboard.",
            CreateModLogEntryError::Unauthorized => "Success. Failed to create moderator log entry because Jim has no permission to send messages.",
            CreateModLogEntryError::Unknown => "Success. Failed to create moderator log entry due to unknown reasons, this incident has been logged."
        }
    }
}

pub async fn create_mod_log_entry(
    http: &Http,
    mod_log_channel_id: ChannelId,
    action_channel_id: Option<ChannelId>,
    mod_user_tag_and_id: &str,
    target_user: &User,
    action: ModLogAction,
    reason: &str,
    entity_id: i32,
    action_time: u64,
) -> Result<(), CreateModLogEntryError> {
    mod_log_channel_id
        .send_message(http, |message| {
            message.embed(|embed| {
                let embed = embed
                    .color(action.color())
                    .timestamp(Utc.timestamp(action_time as i64, 0).to_rfc3339())
                    .field(
                        "Action",
                        format!("{} - #{}", action.name(), entity_id),
                        false,
                    )
                    .field("User:", target_user.tag_and_id(), false)
                    .field("Reason:", reason, false)
                    .field("Responsible Moderator:", mod_user_tag_and_id, false);

                if let Some(action_channel_id) = action_channel_id {
                    embed.field("Channel", action_channel_id.mention(), false);
                }

                action.create_expiration_date_field(embed);
                embed
            })
        })
        .await
        .map(|_| ())
        .map_err(|err| match err.discord_error_code() {
            Some(10003) => CreateModLogEntryError::ModLogChannelDoesNotExist,
            Some(50013) => CreateModLogEntryError::Unauthorized,
            _ => {
                error!("failed to create mod log entry {}", err);
                CreateModLogEntryError::Unknown
            }
        })
}
