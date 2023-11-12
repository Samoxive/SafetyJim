use std::num::{NonZeroU32, NonZeroU64};
use std::time::Duration;

use serenity::builder::EditRole;
use serenity::http::Http;
use serenity::model::channel::{PermissionOverwrite, PermissionOverwriteType};
use serenity::model::id::{ChannelId, GuildId, RoleId, UserId};
use serenity::model::user::User;
use serenity::model::Permissions;
use tracing::{error, warn};
use typemap_rev::TypeMapKey;

use crate::database::mutes::{Mute, MutesRepository};
use crate::database::settings::{get_action_duration_for_auto_mod_action, Setting};
use crate::discord::util::mod_log::{create_mod_log_entry, CreateModLogEntryError, ModLogAction};
use crate::discord::util::user_dm::{notify_user_for_mod_action, ModActionKind};
use crate::discord::util::{execute_mod_action, SerenityErrorExt};
use crate::service::guild::{GetRolesFailure, GuildService};
use crate::service::Services;
use crate::util::now;

impl TypeMapKey for MuteService {
    type Value = MuteService;
}

pub struct MuteService {
    pub repository: MutesRepository,
}

pub enum MuteFailure {
    Unauthorized,
    UnauthorizedFetchRoles,
    UnauthorizedCreateRole,
    UnauthorizedChannelOverride,
    ModLogError(CreateModLogEntryError),
    Unknown,
}

pub enum UnmuteFailure {
    RoleNotFound,
    Unauthorized,
    Unknown,
}

impl MuteService {
    pub async fn fetch_muted_role_id(
        &self,
        http: &Http,
        services: &Services,
        guild_id: GuildId,
    ) -> Result<RoleId, MuteFailure> {
        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            error!("couldn't get guild service!");
            return Err(MuteFailure::Unknown);
        };

        let roles = match guild_service.get_roles(guild_id).await {
            Ok(roles) => roles,
            Err(GetRolesFailure::FetchFailed) => return Err(MuteFailure::UnauthorizedFetchRoles),
        };

        for (id, role) in roles.iter() {
            if role.name == "Muted" {
                return Ok(*id);
            }
        }

        let create_role_req = EditRole::default().name("Muted");
        let role = match guild_id.create_role(http, create_role_req).await {
            Ok(role) => role,
            Err(err) => {
                return match err.discord_error_code() {
                    Some(50013) => Err(MuteFailure::UnauthorizedCreateRole),
                    _ => {
                        error!("failed to create role for guild: {} {}", guild_id, err);
                        Err(MuteFailure::Unknown)
                    }
                };
            }
        };

        let channels = match guild_id.channels(http).await {
            Ok(channels) => channels,
            Err(err) => {
                error!("failed to fetch channels of guild: {} {}", guild_id, err);
                return Err(MuteFailure::Unknown);
            }
        };

        for (_, channel) in channels {
            if let Err(err) = channel
                .create_permission(
                    http,
                    PermissionOverwrite {
                        allow: Default::default(),
                        deny: Permissions::SEND_MESSAGES
                            | Permissions::ADD_REACTIONS
                            | Permissions::SPEAK,
                        kind: PermissionOverwriteType::Role(role.id),
                    },
                )
                .await
            {
                return match err.discord_error_code() {
                    Some(50013) => Err(MuteFailure::UnauthorizedChannelOverride),
                    _ => {
                        error!(
                            "failed to create permission override in guild: {} {}",
                            guild_id, err
                        );
                        Err(MuteFailure::Unknown)
                    }
                };
            }
        }

        Ok(role.id)
    }

    pub async fn issue_mute(
        &self,
        http: &Http,
        guild_id: GuildId,
        guild_name: &str,
        setting: &Setting,
        services: &Services,
        channel_id: Option<ChannelId>,
        mod_user_id: UserId,
        mod_user_tag_and_id: &str,
        target_user: &User,
        reason: String,
        duration: Option<Duration>,
        call_depth: i32,
    ) -> Result<(), MuteFailure> {
        let now = now();
        let expiration_time = duration.map(|duration| now + duration.as_secs());
        let mod_log_channel_id = if setting.mod_log {
            if let Some(id) = NonZeroU64::new(setting.mod_log_channel_id as u64) {
                Some(ChannelId::new(id.get()))
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
            ModActionKind::Mute { expiration_time },
            &reason,
            now,
            guild_name,
            mod_user_tag_and_id,
        )
        .await;

        let role_id = match self.fetch_muted_role_id(http, services, guild_id).await {
            Ok(role) => role,
            Err(err) => {
                return Err(err);
            }
        };

        let audit_log_reason = format!("Muted by {} - {}", mod_user_tag_and_id, reason);
        match http
            .add_member_role(guild_id, target_user.id, role_id, Some(&audit_log_reason))
            .await
        {
            Ok(_) => (),
            Err(err) => {
                return match err.discord_error_code() {
                    Some(50013) => Err(MuteFailure::Unauthorized),
                    _ => {
                        error!("failed to issue discord member role add {}", err);
                        Err(MuteFailure::Unknown)
                    }
                };
            }
        }

        let mute_entry = Mute {
            id: 0,
            user_id: target_user.id.get() as i64,
            moderator_user_id: mod_user_id.get() as i64,
            guild_id: guild_id.get() as i64,
            mute_time: now as i64,
            expire_time: expiration_time.unwrap_or(0) as i64,
            reason: reason.clone(),
            expires: duration.is_some(),
            unmuted: false,
            pardoned: false,
        };

        self.invalidate_previous_user_mutes(guild_id, target_user.id)
            .await;
        let mute_id = self.insert_mute(mute_entry).await.map(|mute| mute.id);

        if let Some(mute_id) = mute_id {
            if let Some(mod_log_channel_id) = mod_log_channel_id {
                if let Err(err) = create_mod_log_entry(
                    http,
                    mod_log_channel_id,
                    channel_id,
                    mod_user_tag_and_id,
                    target_user,
                    ModLogAction::Mute { expiration_time },
                    &reason,
                    mute_id,
                    now,
                )
                .await
                {
                    return Err(MuteFailure::ModLogError(err));
                }
            }
        }

        if setting.mute_threshold != 0 {
            let count = self
                .fetch_actionable_mute_count(guild_id, target_user.id)
                .await;
            if count >= setting.mute_threshold.into() {
                let duration = get_action_duration_for_auto_mod_action(
                    setting.mute_action,
                    setting.mute_action_duration_type,
                    setting.mute_action_duration,
                );

                execute_mod_action(
                    setting.mute_action,
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

    pub async fn unmute(
        &self,
        http: &Http,
        services: &Services,
        guild_id: GuildId,
        target_user_id: UserId,
        mod_user_tag_and_id: &str,
    ) -> Result<(), UnmuteFailure> {
        // use cached roles
        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            error!("couldn't get guild service!");
            return Err(UnmuteFailure::Unknown);
        };

        let roles = match guild_service.get_roles(guild_id).await {
            Ok(roles) => roles,
            Err(GetRolesFailure::FetchFailed) => return Err(UnmuteFailure::Unknown),
        };

        let muted_role = roles.iter().find(|(_, role)| role.name == "Muted");

        let audit_log_reason = format!("Unmuted by {}", mod_user_tag_and_id);

        if let Some((role_id, _)) = muted_role {
            match http
                .remove_member_role(guild_id, target_user_id, *role_id, Some(&audit_log_reason))
                .await
            {
                Ok(_) => (),
                Err(err) => {
                    return match err.discord_error_code() {
                        Some(50013) => Err(UnmuteFailure::Unauthorized),
                        _ => {
                            error!("failed to issue discord member role remove {}", err);
                            Err(UnmuteFailure::Unknown)
                        }
                    };
                }
            }

            self.invalidate_previous_user_mutes(guild_id, target_user_id)
                .await;
        } else {
            return Err(UnmuteFailure::RoleNotFound);
        }

        Ok(())
    }

    pub async fn fetch_mute(&self, id: i32) -> Option<Mute> {
        self.repository
            .fetch_mute(id)
            .await
            .map_err(|err| {
                error!("failed to fetch mute {:?}", err);
                err
            })
            .ok()
            .flatten()
    }

    pub async fn fetch_guild_mutes(&self, guild_id: GuildId, page: NonZeroU32) -> Vec<Mute> {
        self.repository
            .fetch_guild_mutes(guild_id.get() as i64, page.get())
            .await
            .map_err(|err| {
                error!("failed to fetch guild mutes {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn fetch_guild_mute_count(&self, guild_id: GuildId) -> i64 {
        self.repository
            .fetch_guild_mute_count(guild_id.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch guild mute count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn fetch_expired_mutes(&self) -> Vec<Mute> {
        self.repository
            .fetch_expired_mutes()
            .await
            .map_err(|err| {
                error!("failed to fetch expired mutes {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn fetch_valid_mutes(&self, guild_id: GuildId, user_id: UserId) -> Vec<Mute> {
        self.repository
            .fetch_valid_mutes(guild_id.get() as i64, user_id.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch valid mutes {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn fetch_actionable_mute_count(&self, guild_id: GuildId, user_id: UserId) -> i64 {
        self.repository
            .fetch_actionable_mute_count(guild_id.get() as i64, user_id.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch actionable mute count {:?}", err);
                err
            })
            .ok()
            .unwrap_or(0)
    }

    pub async fn insert_mute(&self, mute: Mute) -> Option<Mute> {
        self.repository
            .insert_mute(mute)
            .await
            .map_err(|err| {
                error!("failed to insert mute {:?}", err);
                err
            })
            .ok()
    }

    pub async fn update_mute(&self, mute: Mute) -> Option<()> {
        self.repository
            .update_mute(mute)
            .await
            .map_err(|err| {
                error!("failed to update mute {:?}", err);
                err
            })
            .ok()
    }

    pub async fn invalidate_previous_user_mutes(&self, guild_id: GuildId, user_id: UserId) {
        let _ = self
            .repository
            .invalidate_previous_user_mutes(guild_id.get() as i64, user_id.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to invalidate previous user mutes {:?}", err);
                err
            });
    }

    pub async fn invalidate_mute(&self, id: i32) {
        let _ = self.repository.invalidate_mute(id).await.map_err(|err| {
            error!("failed to invalidate mute {:?}", err);
            err
        });
    }
}
