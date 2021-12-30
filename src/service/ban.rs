use serenity::model::prelude::GuildId;
use std::num::NonZeroU32;
use typemap_rev::TypeMapKey;

use crate::database::bans::{Ban, BansRepository};
use crate::database::settings::Setting;
use crate::discord::util::mod_log::{create_mod_log_entry, CreateModLogEntryError, ModLogAction};
use crate::discord::util::user_dm::{notify_user_for_mod_action, ModActionKind};
use crate::discord::util::SerenityErrorExt;
use crate::util::now;
use serenity::http::Http;
use serenity::model::id::{ChannelId, UserId};
use serenity::model::user::User;
use std::time::Duration;
use tracing::error;

impl TypeMapKey for BanService {
    type Value = BanService;
}

pub struct BanService {
    pub repository: BansRepository,
}

pub enum BanFailure {
    Unauthorized,
    ModLogError(CreateModLogEntryError),
    Unknown,
}

pub enum UnbanFailure {
    UserNotBanned,
    Unauthorized,
    Unknown,
}

impl BanService {
    pub async fn issue_ban(
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
        duration: Option<Duration>,
    ) -> Result<(), BanFailure> {
        let now = now();
        let expiration_time = duration.map(|duration| now + duration.as_secs());

        let mod_log_channel_id = if setting.mod_log {
            Some(ChannelId(setting.mod_log_channel_id as u64))
        } else {
            None
        };

        notify_user_for_mod_action(
            http,
            target_user.id,
            ModActionKind::Ban { expiration_time },
            &reason,
            now,
            guild_name,
            mod_user_tag_and_id,
        )
        .await;

        let audit_log_reason = format!("Banned by {} - {}", mod_user_tag_and_id, reason);
        match guild_id
            .ban_with_reason(http, target_user.id, 0, audit_log_reason)
            .await
        {
            Ok(_) => (),
            Err(err) => {
                return match err.discord_error_code() {
                    Some(50013) => Err(BanFailure::Unauthorized),
                    _ => {
                        error!("failed to issue discord ban {}", err);
                        Err(BanFailure::Unknown)
                    }
                }
            }
        }

        let ban_entry = Ban {
            id: 0,
            user_id: target_user.id.0 as i64,
            moderator_user_id: mod_user_id.0 as i64,
            guild_id: guild_id.0 as i64,
            ban_time: now as i64,
            expire_time: expiration_time.unwrap_or(0) as i64,
            reason: reason.clone(),
            expires: duration.is_some(),
            unbanned: false,
        };

        // we already issued the ban, we can ignore whether inserting to database failed or not
        // chances are it will not fail anyways.
        self.invalidate_previous_user_bans(guild_id, target_user.id)
            .await;
        let ban_id = self.insert_ban(ban_entry).await.map(|ban| ban.id);

        if let Some(ban_id) = ban_id {
            if let Some(mod_log_channel_id) = mod_log_channel_id {
                if let Err(err) = create_mod_log_entry(
                    http,
                    mod_log_channel_id,
                    channel_id,
                    mod_user_tag_and_id,
                    target_user,
                    ModLogAction::Ban { expiration_time },
                    &reason,
                    ban_id,
                    now,
                )
                .await
                {
                    return Err(BanFailure::ModLogError(err));
                }
            }
        }

        Ok(())
    }

    pub async fn unban(
        &self,
        http: &Http,
        guild_id: GuildId,
        user_id: UserId,
    ) -> Result<(), UnbanFailure> {
        match guild_id.unban(http, user_id).await {
            Ok(_) => (),
            Err(err) => {
                return match err.discord_error_code() {
                    Some(10026) => Err(UnbanFailure::UserNotBanned),
                    Some(50013) => Err(UnbanFailure::Unauthorized),
                    _ => {
                        error!("failed issue discord unban {}", err);
                        Err(UnbanFailure::Unknown)
                    }
                }
            }
        }

        self.invalidate_previous_user_bans(guild_id, user_id).await;
        Ok(())
    }

    pub async fn fetch_ban(&self, id: i32) -> Option<Ban> {
        self.repository
            .fetch_ban(id)
            .await
            .map_err(|err| {
                error!("failed to fetch ban {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn fetch_guild_bans(&self, guild_id: GuildId, page: NonZeroU32) -> Vec<Ban> {
        self.repository
            .fetch_guild_bans(guild_id.0 as i64, page.get())
            .await
            .map_err(|err| {
                error!("failed to fetch guild bans {:?}", err);
                err
            })
            .ok()
            .unwrap()
    }

    pub async fn fetch_guild_ban_count(&self, guild_id: GuildId) -> i64 {
        self.repository
            .fetch_guild_ban_count(guild_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild ban count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn fetch_expired_bans(&self) -> Vec<Ban> {
        self.repository
            .fetch_expired_bans()
            .await
            .map_err(|err| {
                error!("failed to fetch expired bans {:?}", err);
                err
            })
            .ok()
            .unwrap_or_else(Vec::new)
    }

    pub async fn fetch_last_guild_ban(&self, guild_id: GuildId) -> Option<Ban> {
        self.repository
            .fetch_last_guild_ban(guild_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch last guild ban {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn insert_ban(&self, ban: Ban) -> Option<Ban> {
        self.repository
            .insert_ban(ban)
            .await
            .map_err(|err| {
                error!("failed to insert ban {:?}", err);
                err
            })
            .ok()
    }

    pub async fn update_ban(&self, ban: Ban) -> Option<()> {
        self.repository
            .update_ban(ban)
            .await
            .map_err(|err| {
                error!("failed to update ban {:?}", err);
                err
            })
            .ok()
    }

    pub async fn invalidate_previous_user_bans(&self, guild_id: GuildId, user_id: UserId) {
        let _ = self
            .repository
            .invalidate_previous_user_bans(guild_id.0 as i64, user_id.0 as i64)
            .await
            .map_err(|err| {
                error!("failed to invalidate user bans {:?}", err);
                err
            });
    }

    pub async fn invalidate_ban(&self, id: i32) {
        let _ = self.repository.invalidate_ban(id).await.map_err(|err| {
            error!("failed to invalidate ban {:?}", err);
            err
        });
    }
}
