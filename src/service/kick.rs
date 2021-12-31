use serenity::model::id::{ChannelId, GuildId, UserId};
use std::num::NonZeroU32;
use typemap_rev::{TypeMap, TypeMapKey};

use crate::database::kicks::{Kick, KicksRepository};
use crate::database::settings::{get_action_duration_for_auto_mod_action, Setting};
use crate::discord::util::mod_log::{create_mod_log_entry, CreateModLogEntryError, ModLogAction};
use crate::discord::util::user_dm::{notify_user_for_mod_action, ModActionKind};
use crate::discord::util::{execute_mod_action, SerenityErrorExt};
use crate::util::now;
use serenity::http::Http;
use serenity::model::user::User;
use tracing::error;

impl TypeMapKey for KickService {
    type Value = KickService;
}

pub struct KickService {
    pub repository: KicksRepository,
}

pub enum KickFailure {
    Unauthorized,
    ModLogError(CreateModLogEntryError),
    Unknown,
}

impl KickService {
    pub async fn issue_kick(
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
    ) -> Result<(), KickFailure> {
        let now = now();
        let mod_log_channel_id = if setting.mod_log {
            Some(ChannelId(setting.mod_log_channel_id as u64))
        } else {
            None
        };

        notify_user_for_mod_action(
            http,
            target_user.id,
            ModActionKind::Kick,
            &reason,
            now,
            guild_name,
            mod_user_tag_and_id,
        )
        .await;

        let audit_log_reason = format!("Banned by {} - {}", mod_user_tag_and_id, reason);
        match guild_id
            .kick_with_reason(http, target_user.id, &audit_log_reason)
            .await
        {
            Ok(_) => (),
            Err(err) => {
                return match err.discord_error_code() {
                    Some(50013) => Err(KickFailure::Unauthorized),
                    _ => {
                        error!("failed to issue discord kick {}", err);
                        Err(KickFailure::Unknown)
                    }
                }
            }
        }

        let kick_entry = Kick {
            id: 0,
            user_id: target_user.id.0 as i64,
            moderator_user_id: mod_user_id.0 as i64,
            guild_id: guild_id.0 as i64,
            kick_time: now as i64,
            reason: reason.clone(),
            pardoned: false,
        };

        let kick_id = self.insert_kick(kick_entry).await.map(|kick| kick.id);

        if let Some(kick_id) = kick_id {
            if let Some(mod_log_channel_id) = mod_log_channel_id {
                if let Err(err) = create_mod_log_entry(
                    http,
                    mod_log_channel_id,
                    channel_id,
                    mod_user_tag_and_id,
                    target_user,
                    ModLogAction::Kick,
                    &reason,
                    kick_id,
                    now,
                )
                .await
                {
                    return Err(KickFailure::ModLogError(err));
                }
            }
        }

        if setting.kick_threshold != 0 {
            let count = self
                .fetch_actionable_kick_count(guild_id, target_user.id)
                .await;
            if count >= setting.kick_threshold.into() {
                let duration = get_action_duration_for_auto_mod_action(
                    setting.kick_action,
                    setting.kick_action_duration_type,
                    setting.kick_action_duration,
                );

                execute_mod_action(
                    setting.kick_action,
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

    pub async fn fetch_kick(&self, id: i32) -> Option<Kick> {
        self.repository
            .fetch_kick(id)
            .await
            .map_err(|err| {
                error!("failed to fetch kick {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn fetch_guild_kicks(&self, guild_id: GuildId, page: NonZeroU32) -> Vec<Kick> {
        self.repository
            .fetch_guild_kicks(guild_id.0 as i64, page.get())
            .await
            .map_err(|err| {
                error!("failed to fetch guild kicks {:?}", err);
                err
            })
            .ok()
            .unwrap_or_else(Vec::new)
    }

    pub async fn fetch_guild_kick_count(&self, guild_id: GuildId) -> i64 {
        self.repository
            .fetch_guild_kick_count(guild_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild kick count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn fetch_actionable_kick_count(&self, guild_id: GuildId, user_id: UserId) -> i64 {
        self.repository
            .fetch_actionable_kick_count(guild_id.0 as i64, user_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch actionable kick count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn insert_kick(&self, kick: Kick) -> Option<Kick> {
        self.repository
            .insert_kick(kick)
            .await
            .map_err(|err| {
                error!("failed to insert kick {:?}", err);
                err
            })
            .ok()
    }

    pub async fn update_kick(&self, kick: Kick) -> Option<()> {
        self.repository
            .update_kick(kick)
            .await
            .map_err(|err| {
                error!("failed to update kick {:?}", err);
                err
            })
            .ok()
    }
}
