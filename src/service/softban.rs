use std::num::{NonZeroU32, NonZeroU64};

use serenity::http::Http;
use serenity::model::id::{ChannelId, GuildId, UserId};
use serenity::model::user::User;
use tracing::{error, warn};
use typemap_rev::{TypeMap, TypeMapKey};

use crate::database::settings::{get_action_duration_for_auto_mod_action, Setting};
use crate::database::softbans::{Softban, SoftbansRepository};
use crate::discord::util::mod_log::{create_mod_log_entry, CreateModLogEntryError, ModLogAction};
use crate::discord::util::user_dm::{notify_user_for_mod_action, ModActionKind};
use crate::discord::util::{execute_mod_action, SerenityErrorExt};
use crate::util::now;

impl TypeMapKey for SoftbanService {
    type Value = SoftbanService;
}

pub struct SoftbanService {
    pub repository: SoftbansRepository,
}

pub enum SoftbanFailure {
    Unauthorized,
    ModLogError(CreateModLogEntryError),
    Unknown,
}

impl SoftbanService {
    pub async fn issue_softban(
        &self,
        http: &Http,
        guild_id: GuildId,
        guild_name: &str,
        setting: &Setting,
        services: &TypeMap,
        channel_id: Option<ChannelId>,
        mod_user_id: UserId,
        mod_user_tag_and_id: &str,
        target_user: &User,
        reason: String,
        days: u8,
        call_depth: i32,
    ) -> Result<(), SoftbanFailure> {
        let now = now();
        let mod_log_channel_id = if setting.mod_log {
            if let Some(id) = NonZeroU64::new(setting.mod_log_channel_id as u64) {
                Some(ChannelId(id))
            } else {
                warn!(
                    "found setting with invalid mod log channel id! {:?}",
                    setting
                );
                None
            }
        } else {
            None
        };

        notify_user_for_mod_action(
            http,
            target_user.id,
            ModActionKind::Softban,
            &reason,
            now,
            guild_name,
            mod_user_tag_and_id,
        )
        .await;

        let audit_log_reason = format!("Softbanned by {} - {}", mod_user_tag_and_id, reason);
        match guild_id
            .ban_with_reason(http, target_user.id, days, audit_log_reason)
            .await
        {
            Ok(_) => (),
            Err(err) => {
                return match err.discord_error_code() {
                    Some(50013) => Err(SoftbanFailure::Unauthorized),
                    _ => {
                        error!("failed to issue discord softban {}", err);
                        Err(SoftbanFailure::Unknown)
                    }
                };
            }
        }

        match guild_id.unban(http, target_user.id).await {
            Ok(_) => (),
            Err(err) => {
                return match err.discord_error_code() {
                    Some(50013) => Err(SoftbanFailure::Unauthorized),
                    _ => {
                        error!("failed to issue discord unban {}", err);
                        Err(SoftbanFailure::Unknown)
                    }
                };
            }
        }

        let softban_entry = Softban {
            id: 0,
            user_id: target_user.id.0.get() as i64,
            moderator_user_id: mod_user_id.0.get() as i64,
            guild_id: guild_id.0.get() as i64,
            softban_time: now as i64,
            reason: reason.clone(),
            pardoned: false,
        };

        let softban_id = self
            .insert_softban(softban_entry)
            .await
            .map(|softban| softban.id);

        if let Some(softban_id) = softban_id {
            if let Some(mod_log_channel_id) = mod_log_channel_id {
                if let Err(err) = create_mod_log_entry(
                    http,
                    mod_log_channel_id,
                    channel_id,
                    mod_user_tag_and_id,
                    target_user,
                    ModLogAction::Softban,
                    &reason,
                    softban_id,
                    now,
                )
                .await
                {
                    return Err(SoftbanFailure::ModLogError(err));
                }
            }
        }

        if setting.softban_threshold != 0 {
            let count = self
                .fetch_actionable_softban_count(guild_id, target_user.id)
                .await;
            if count >= setting.softban_threshold.into() {
                let duration = get_action_duration_for_auto_mod_action(
                    setting.softban_action,
                    setting.softban_action_duration_type,
                    setting.softban_action_duration,
                );

                execute_mod_action(
                    setting.softban_action,
                    http,
                    guild_id,
                    guild_name,
                    setting,
                    services,
                    channel_id,
                    mod_user_id,
                    mod_user_tag_and_id,
                    target_user,
                    reason,
                    duration,
                    call_depth,
                )
                .await;
            }
        }

        Ok(())
    }

    pub async fn fetch_softban(&self, id: i32) -> Option<Softban> {
        self.repository
            .fetch_softban(id)
            .await
            .map_err(|err| {
                error!("failed to fetch softban {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn fetch_guild_softbans(&self, guild_id: GuildId, page: NonZeroU32) -> Vec<Softban> {
        self.repository
            .fetch_guild_softbans(guild_id.0.get() as i64, page.get())
            .await
            .map_err(|err| {
                error!("failed to fetch guild softbans {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn fetch_guild_softban_count(&self, guild_id: GuildId) -> i64 {
        self.repository
            .fetch_guild_softban_count(guild_id.0.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild softban count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn fetch_actionable_softban_count(&self, guild_id: GuildId, user_id: UserId) -> i64 {
        self.repository
            .fetch_actionable_softban_count(guild_id.0.get() as i64, user_id.0.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch actionable softban count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn insert_softban(&self, softban: Softban) -> Option<Softban> {
        self.repository
            .insert_softban(softban)
            .await
            .map_err(|err| {
                error!("failed to insert softban {:?}", err);
                err
            })
            .ok()
    }

    pub async fn update_softban(&self, softban: Softban) -> Option<()> {
        self.repository
            .update_softban(softban)
            .await
            .map_err(|err| {
                error!("failed to update softban {:?}", err);
                err
            })
            .ok()
    }
}
