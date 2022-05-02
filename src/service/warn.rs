use serenity::model::id::{ChannelId, GuildId, UserId};
use std::num::NonZeroU32;
use typemap_rev::{TypeMap, TypeMapKey};

use crate::database::settings::{get_action_duration_for_auto_mod_action, Setting};
use crate::database::warns::{Warn, WarnsRepository};
use crate::discord::util::execute_mod_action;
use crate::discord::util::mod_log::{create_mod_log_entry, CreateModLogEntryError, ModLogAction};
use crate::discord::util::user_dm::{notify_user_for_mod_action, ModActionKind};
use crate::util::now;
use serenity::http::Http;
use serenity::model::user::User;
use tracing::error;

impl TypeMapKey for WarnService {
    type Value = WarnService;
}

pub struct WarnService {
    pub repository: WarnsRepository,
}

pub enum WarnFailure {
    ModLogError(CreateModLogEntryError),
}

impl WarnService {
    pub async fn issue_warn(
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
        call_depth: i32,
    ) -> Result<(), WarnFailure> {
        let now = now();
        let mod_log_channel_id = if setting.mod_log {
            Some(ChannelId(setting.mod_log_channel_id as u64))
        } else {
            None
        };

        notify_user_for_mod_action(
            http,
            target_user.id,
            ModActionKind::Warn,
            &reason,
            now,
            guild_name,
            mod_user_tag_and_id,
        )
        .await;

        let warn_entry = Warn {
            id: 0,
            user_id: target_user.id.0 as i64,
            moderator_user_id: mod_user_id.0 as i64,
            guild_id: guild_id.0 as i64,
            warn_time: now as i64,
            reason: reason.clone(),
            pardoned: false,
        };

        let warn_id = self.insert_warn(warn_entry).await.map(|warn| warn.id);

        if let Some(warn_id) = warn_id {
            if let Some(mod_log_channel_id) = mod_log_channel_id {
                if let Err(err) = create_mod_log_entry(
                    http,
                    mod_log_channel_id,
                    channel_id,
                    mod_user_tag_and_id,
                    target_user,
                    ModLogAction::Warn,
                    &reason,
                    warn_id,
                    now,
                )
                .await
                {
                    return Err(WarnFailure::ModLogError(err));
                }
            }
        }

        if setting.warn_threshold != 0 {
            let count = self
                .fetch_actionable_warn_count(guild_id, target_user.id)
                .await;
            if count >= setting.warn_threshold.into() {
                let duration = get_action_duration_for_auto_mod_action(
                    setting.warn_action,
                    setting.warn_action_duration_type,
                    setting.warn_action_duration,
                );

                execute_mod_action(
                    setting.warn_action,
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

    pub async fn fetch_warn(&self, id: i32) -> Option<Warn> {
        self.repository
            .fetch_warn(id)
            .await
            .map_err(|err| {
                error!("failed to fetch warn {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn fetch_guild_warns(&self, guild_id: GuildId, page: NonZeroU32) -> Vec<Warn> {
        self.repository
            .fetch_guild_warns(guild_id.0 as i64, page.get())
            .await
            .map_err(|err| {
                error!("failed to fetch guild warns {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn fetch_guild_warn_count(&self, guild_id: GuildId) -> i64 {
        self.repository
            .fetch_guild_warn_count(guild_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild warn count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn fetch_actionable_warn_count(&self, guild_id: GuildId, user_id: UserId) -> i64 {
        self.repository
            .fetch_actionable_warn_count(guild_id.0 as i64, user_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch actionable warn count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn insert_warn(&self, warn: Warn) -> Option<Warn> {
        self.repository
            .insert_warn(warn)
            .await
            .map_err(|err| {
                error!("failed to insert warn {:?}", err);
                err
            })
            .ok()
    }

    pub async fn update_warn(&self, warn: Warn) -> Option<()> {
        self.repository
            .update_warn(warn)
            .await
            .map_err(|err| {
                error!("failed to update warn {:?}", err);
                err
            })
            .ok()
    }
}
