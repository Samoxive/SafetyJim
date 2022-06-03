use std::num::NonZeroU32;

use serenity::http::Http;
use serenity::model::id::{ChannelId, GuildId, UserId};
use serenity::model::user::User;
use tracing::error;
use typemap_rev::TypeMapKey;

use crate::database::hardbans::{Hardban, HardbansRepository};
use crate::database::settings::Setting;
use crate::discord::util::mod_log::{create_mod_log_entry, CreateModLogEntryError, ModLogAction};
use crate::discord::util::user_dm::{notify_user_for_mod_action, ModActionKind};
use crate::discord::util::SerenityErrorExt;
use crate::util::now;

impl TypeMapKey for HardbanService {
    type Value = HardbanService;
}

pub struct HardbanService {
    pub repository: HardbansRepository,
}

pub enum HardbanFailure {
    Unauthorized,
    ModLogError(CreateModLogEntryError),
    Unknown,
}

impl HardbanService {
    pub async fn issue_hardban(
        &self,
        http: &Http,
        guild_id: GuildId,
        guild_name: &str,
        setting: &Setting,
        channel_id: Option<ChannelId>,
        mod_user_id: UserId,
        mod_user_tag_and_id: &str,
        target_user: &User,
        reason: String,
    ) -> Result<(), HardbanFailure> {
        let now = now();
        let mod_log_channel_id = if setting.mod_log {
            Some(ChannelId(setting.mod_log_channel_id as u64))
        } else {
            None
        };

        notify_user_for_mod_action(
            http,
            target_user.id,
            ModActionKind::Hardban,
            &reason,
            now,
            guild_name,
            mod_user_tag_and_id,
        )
        .await;

        let audit_log_reason = format!("Hardbanned by {} - {}", mod_user_tag_and_id, reason);
        match guild_id
            .ban_with_reason(http, target_user.id, 7, audit_log_reason)
            .await
        {
            Ok(_) => (),
            Err(err) => {
                return match err.discord_error_code() {
                    Some(50013) => Err(HardbanFailure::Unauthorized),
                    _ => {
                        error!("failed to issue discord hardban {}", err);
                        Err(HardbanFailure::Unknown)
                    }
                };
            }
        }

        let hardban_entry = Hardban {
            id: 0,
            user_id: target_user.id.0 as i64,
            moderator_user_id: mod_user_id.0 as i64,
            guild_id: guild_id.0 as i64,
            hardban_time: now as i64,
            reason: reason.clone(),
        };

        let hardban_id = self
            .insert_hardban(hardban_entry)
            .await
            .map(|hardban| hardban.id);

        if let Some(hardban_id) = hardban_id {
            if let Some(mod_log_channel_id) = mod_log_channel_id {
                if let Err(err) = create_mod_log_entry(
                    http,
                    mod_log_channel_id,
                    channel_id,
                    mod_user_tag_and_id,
                    target_user,
                    ModLogAction::Hardban,
                    &reason,
                    hardban_id,
                    now,
                )
                .await
                {
                    return Err(HardbanFailure::ModLogError(err));
                }
            }
        }

        Ok(())
    }

    pub async fn fetch_hardban(&self, id: i32) -> Option<Hardban> {
        self.repository
            .fetch_hardban(id)
            .await
            .map_err(|err| {
                error!("failed to fetch guild hardban {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn fetch_guild_hardbans(&self, guild_id: GuildId, page: NonZeroU32) -> Vec<Hardban> {
        self.repository
            .fetch_guild_hardbans(guild_id.0 as i64, page.get())
            .await
            .map_err(|err| {
                error!("failed to fetch guild guild hardbans {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn fetch_guild_hardban_count(&self, guild_id: GuildId) -> i64 {
        self.repository
            .fetch_guild_hardban_count(guild_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild hardban count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn insert_hardban(&self, hardban: Hardban) -> Option<Hardban> {
        self.repository
            .insert_hardban(hardban)
            .await
            .map_err(|err| {
                error!("failed to insert hardban {:?}", err);
                err
            })
            .ok()
    }

    pub async fn update_hardban(&self, hardban: Hardban) -> Option<()> {
        self.repository
            .update_hardban(hardban)
            .await
            .map_err(|err| {
                error!("failed to update hardban {:?}", err);
                err
            })
            .ok()
    }
}
