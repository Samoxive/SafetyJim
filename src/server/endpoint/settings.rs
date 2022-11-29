use std::num::NonZeroU64;
use std::sync::Arc;

use async_trait::async_trait;
use axum::extract::{FromRequestParts, Path, State};
use axum::http::request::Parts;
use axum::http::{Method, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::Json;
use serenity::all::{ChannelId, GuildId, RoleId};
use serenity::model::Permissions;

use crate::server::model::channel::ChannelModel;
use crate::server::model::guild::GuildModel;
use crate::server::model::role::RoleModel;
use crate::server::model::setting::SettingModel;
use crate::server::{extract_service, AxumState, GuildPathParams, User};
use crate::database::settings::{
    Setting, ACTION_HARDBAN, ACTION_NOTHING, DURATION_TYPE_DAYS, DURATION_TYPE_SECONDS,
    PRIVACY_ADMIN_ONLY, PRIVACY_EVERYONE, PRIVACY_STAFF_ONLY, WORD_FILTER_LEVEL_HIGH,
    WORD_FILTER_LEVEL_LOW,
};
use crate::discord::util::is_staff;
use crate::service::guild::GuildService;
use crate::service::setting::SettingService;
use crate::service::Services;

pub struct SettingEndpointParams(GuildId);

#[async_trait]
impl FromRequestParts<AxumState> for SettingEndpointParams {
    type Rejection = Response;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AxumState,
    ) -> Result<Self, Self::Rejection> {
        let is_read = match parts.method {
            Method::GET => true,
            Method::POST => false,
            Method::DELETE => false,
            _ => return Err(StatusCode::METHOD_NOT_ALLOWED.into_response()),
        };

        let Path(GuildPathParams { guild_id }) =
            Path::<GuildPathParams>::from_request_parts(parts, state)
                .await
                .map_err(|_err| StatusCode::INTERNAL_SERVER_ERROR.into_response())?;

        let guild_id = GuildId(guild_id);

        let User(user_id) = User::from_request_parts(parts, state)
            .await
            .map_err(|err| err.into_response())?;

        let guild_service =
            extract_service::<GuildService>(&state.services).map_err(|err| err.into_response())?;

        let member = guild_service
            .get_member(guild_id, user_id)
            .await
            .map_err(|_err| {
                (
                    StatusCode::FORBIDDEN,
                    Json("This server either doesn't exist or you aren't in it!"),
                )
                    .into_response()
            })?;

        let permissions = guild_service
            .get_permissions(user_id, &member.roles, guild_id)
            .await
            .map_err(|_err| {
                (
                    StatusCode::FORBIDDEN,
                    Json("This server either doesn't exist or you aren't in it!"),
                )
                    .into_response()
            })?;

        let setting_service = extract_service::<SettingService>(&state.services)
            .map_err(|err| err.into_response())?;
        let setting = setting_service.get_setting(guild_id).await;

        let privacy_setting = setting.privacy_settings;
        if is_read {
            let is_authorized = if privacy_setting == PRIVACY_EVERYONE {
                true
            } else if privacy_setting == PRIVACY_STAFF_ONLY {
                is_staff(permissions)
            } else if privacy_setting == PRIVACY_ADMIN_ONLY {
                permissions.administrator()
            } else {
                false
            };

            if !is_authorized {
                return Err((
                    StatusCode::FORBIDDEN,
                    Json("Server settings prevent you from viewing private information!"),
                )
                    .into_response());
            }
        } else {
            let is_authorized = permissions.contains(Permissions::ADMINISTRATOR);

            if !is_authorized {
                return Err((
                    StatusCode::FORBIDDEN,
                    Json("You don't have permissions to change server settings!"),
                )
                    .into_response());
            }
        }

        Ok(SettingEndpointParams(guild_id))
    }
}

// /guilds/:guild_id/settings
pub async fn get_setting(
    State(services): State<Arc<Services>>,
    SettingEndpointParams(guild_id): SettingEndpointParams,
) -> Result<Json<SettingModel>, Response> {
    let guild_service =
        extract_service::<GuildService>(&services).map_err(|err| err.into_response())?;

    // TODO(sam): front end doesn't really use this data, remove?
    let guild = match guild_service.get_guild(guild_id).await {
        Ok(guild) => guild,
        Err(_) => {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Failed to fetch guild data, is Jim in this server?"),
            )
                .into_response());
        }
    };

    let channels = match guild_service.get_channels(guild_id).await {
        Ok(channels) => channels,
        Err(_) => {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Failed to fetch guild data, is Jim in this server?"),
            )
                .into_response());
        }
    };

    let roles = match guild_service.get_roles(guild_id).await {
        Ok(roles) => roles,
        Err(_) => {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Failed to fetch guild data, is Jim in this server?"),
            )
                .into_response());
        }
    };

    let setting_service =
        extract_service::<SettingService>(&services).map_err(|err| err.into_response())?;

    let setting = setting_service.get_setting(guild_id).await;

    let mod_log_channel = NonZeroU64::new(setting.mod_log_channel_id as u64)
        .map(ChannelId)
        .and_then(|channel_id| {
            channels
                .get(&channel_id)
                .map(|channel| (channel_id, channel))
        })
        .map(|(id, channel)| ChannelModel::from_guild_channel(id, channel));

    let holding_room_role = setting
        .holding_room_role_id
        .and_then(|id| NonZeroU64::new(id as u64))
        .map(RoleId)
        .and_then(|role_id| roles.get(&role_id).map(|role| (role_id, role)))
        .map(|(role_id, role)| RoleModel::from_role(role_id, role));

    let welcome_channel = NonZeroU64::new(setting.welcome_message_channel_id as u64)
        .map(ChannelId)
        .and_then(|channel_id| {
            channels
                .get(&channel_id)
                .map(|channel| (channel_id, channel))
        })
        .map(|(id, channel)| ChannelModel::from_guild_channel(id, channel));

    Ok(Json(SettingModel {
        guild: GuildModel::from_cached_guild(guild_id, &guild),
        channels: channels
            .iter()
            .map(|(id, channel)| ChannelModel::from_guild_channel(*id, channel))
            .collect(),
        roles: roles
            .iter()
            .map(|(id, role)| RoleModel::from_role(*id, role))
            .collect(),
        mod_log: setting.mod_log,
        mod_log_channel,
        holding_room: setting.holding_room,
        holding_room_role,
        holding_room_minutes: setting.holding_room_minutes,
        invite_link_remover: setting.invite_link_remover,
        welcome_message: setting.welcome_message,
        message: setting.message.clone(),
        welcome_message_channel: welcome_channel,
        join_captcha: setting.join_captcha,
        word_filter: setting.word_filter,
        word_filter_blocklist: setting.word_filter_blocklist.clone(),
        word_filter_level: setting.word_filter_level,
        word_filter_action: setting.word_filter_action,
        word_filter_action_duration: setting.word_filter_action_duration,
        word_filter_action_duration_type: setting.word_filter_action_duration_type,
        invite_link_remover_action: setting.invite_link_remover_action,
        invite_link_remover_action_duration: setting.invite_link_remover_action_duration,
        invite_link_remover_action_duration_type: setting.invite_link_remover_action_duration_type,
        privacy_settings: setting.privacy_settings,
        privacy_mod_log: setting.privacy_mod_log,
        softban_threshold: setting.softban_threshold,
        softban_action: setting.softban_action,
        softban_action_duration: setting.softban_action_duration,
        softban_action_duration_type: setting.softban_action_duration_type,
        kick_threshold: setting.kick_threshold,
        kick_action: setting.kick_action,
        kick_action_duration: setting.kick_action_duration,
        kick_action_duration_type: setting.kick_action_duration_type,
        mute_threshold: setting.mute_threshold,
        mute_action: setting.mute_action,
        mute_action_duration: setting.mute_action_duration,
        mute_action_duration_type: setting.mute_action_duration_type,
        warn_threshold: setting.warn_threshold,
        warn_action: setting.warn_action,
        warn_action_duration: setting.warn_action_duration,
        warn_action_duration_type: setting.warn_action_duration_type,
        mods_can_edit_tags: setting.mods_can_edit_tags,
        spam_filter: setting.spam_filter,
    }))
}

// /guilds/:guild_id/settings
pub async fn update_setting(
    State(services): State<Arc<Services>>,
    SettingEndpointParams(guild_id): SettingEndpointParams,
    Json(mut new_setting): Json<SettingModel>,
) -> Result<(), Response> {
    let guild_service =
        extract_service::<GuildService>(&services).map_err(|err| err.into_response())?;

    let channels = match guild_service.get_channels(guild_id).await {
        Ok(channels) => channels,
        Err(_) => {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Failed to fetch guild data, is Jim in this server?"),
            )
                .into_response());
        }
    };

    let roles = match guild_service.get_roles(guild_id).await {
        Ok(roles) => roles,
        Err(_) => {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Failed to fetch guild data, is Jim in this server?"),
            )
                .into_response());
        }
    };

    new_setting.message = new_setting.message.trim().to_string();
    new_setting.word_filter_blocklist = new_setting
        .word_filter_blocklist
        .as_ref()
        .map(|s| s.trim().to_string());

    if let Some(blocklist) = new_setting.word_filter_blocklist.as_ref() {
        if blocklist.is_empty() {
            new_setting.word_filter_blocklist = None
        }
    }

    let mod_log_channel_id = if let Some(channel) = new_setting.mod_log_channel.as_ref() {
        let channel_id = match channel.id.parse::<NonZeroU64>() {
            Ok(id) => ChannelId(id),
            Err(_) => {
                return Err((
                    StatusCode::BAD_REQUEST,
                    Json("Selected moderator log channel id is invalid!"),
                )
                    .into_response());
            }
        };

        if channels.get(&channel_id).is_none() {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Selected moderator log channel doesn't exist!"),
            )
                .into_response());
        }

        channel_id.get() as i64
    } else {
        0
    };

    let welcome_message_channel_id =
        if let Some(channel) = new_setting.welcome_message_channel.as_ref() {
            let channel_id = match channel.id.parse::<NonZeroU64>() {
                Ok(id) => ChannelId(id),
                Err(_) => {
                    return Err((
                        StatusCode::BAD_REQUEST,
                        Json("Selected welcome message channel id is invalid!"),
                    )
                        .into_response());
                }
            };

            if channels.get(&channel_id).is_none() {
                return Err((
                    StatusCode::BAD_REQUEST,
                    Json("Selected welcome message channel doesn't exist!"),
                )
                    .into_response());
            }

            channel_id.get() as i64
        } else {
            0
        };

    let holding_room_role_id = if let Some(role) = new_setting.holding_room_role.as_ref() {
        let role_id = match role.id.parse::<NonZeroU64>() {
            Ok(id) => RoleId(id),
            Err(_) => {
                return Err((
                    StatusCode::BAD_REQUEST,
                    Json("Selected holding room role id is invalid!"),
                )
                    .into_response());
            }
        };

        if roles.get(&role_id).is_none() {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Selected holding room role doesn't exist!"),
            )
                .into_response());
        }

        Some(role_id.get() as i64)
    } else {
        if new_setting.join_captcha || new_setting.holding_room {
            return Err((StatusCode::BAD_REQUEST, Json("You can't enable join captcha or holding room without setting a holding room role!")).into_response());
        }

        None
    };

    if new_setting.join_captcha && new_setting.holding_room {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("You can't enable both holding room and join captcha at the same time!"),
        )
            .into_response());
    }

    if new_setting.holding_room_minutes < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Holding room minutes cannot be negative!"),
        )
            .into_response());
    }

    if new_setting.message.is_empty() {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Welcome message cannot be empty!"),
        )
            .into_response());
    } else if new_setting.message.len() >= 1750 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Welcome message cannot be too long!"),
        )
            .into_response());
    }

    if let Some(blocklist) = new_setting.word_filter_blocklist.as_ref() {
        if blocklist.len() > 2000 {
            return Err((
                StatusCode::BAD_REQUEST,
                Json("Word filter blocklist cannot be too long!"),
            )
                .into_response());
        }
    }

    if new_setting.word_filter_level != WORD_FILTER_LEVEL_LOW
        && new_setting.word_filter_level != WORD_FILTER_LEVEL_HIGH
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for word filter level!"),
        )
            .into_response());
    }

    if new_setting.word_filter_action < ACTION_NOTHING
        || new_setting.word_filter_action > ACTION_HARDBAN
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for word filter action!"),
        )
            .into_response());
    }

    if new_setting.word_filter_action_duration < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for word filter action duration!"),
        )
            .into_response());
    }

    if new_setting.word_filter_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.word_filter_action_duration_type > DURATION_TYPE_DAYS
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for word filter action duration type!"),
        )
            .into_response());
    }

    if new_setting.invite_link_remover_action < ACTION_NOTHING
        || new_setting.invite_link_remover_action > ACTION_HARDBAN
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for invite link remover action!"),
        )
            .into_response());
    }

    if new_setting.invite_link_remover_action_duration < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for invite link remover action duration!"),
        )
            .into_response());
    }

    if new_setting.invite_link_remover_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.invite_link_remover_action_duration_type > DURATION_TYPE_DAYS
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for invite link remover action duration type!"),
        )
            .into_response());
    }

    if new_setting.softban_threshold < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for softban threshold!"),
        )
            .into_response());
    }

    if new_setting.softban_action < ACTION_NOTHING || new_setting.softban_action > ACTION_HARDBAN {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for softban action!"),
        )
            .into_response());
    }

    if new_setting.softban_action_duration < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for softban action duration!"),
        )
            .into_response());
    }

    if new_setting.softban_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.softban_action_duration_type > DURATION_TYPE_DAYS
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for softban action duration type!"),
        )
            .into_response());
    }

    if new_setting.kick_threshold < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for kick threshold!"),
        )
            .into_response());
    }

    if new_setting.kick_action < ACTION_NOTHING || new_setting.kick_action > ACTION_HARDBAN {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for kick action!"),
        )
            .into_response());
    }

    if new_setting.kick_action_duration < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for kick action duration!"),
        )
            .into_response());
    }

    if new_setting.kick_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.kick_action_duration_type > DURATION_TYPE_DAYS
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for kick action duration type!"),
        )
            .into_response());
    }

    if new_setting.mute_threshold < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for mute threshold!"),
        )
            .into_response());
    }

    if new_setting.mute_action < ACTION_NOTHING || new_setting.mute_action > ACTION_HARDBAN {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for mute action!"),
        )
            .into_response());
    }

    if new_setting.mute_action_duration < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for mute action duration!"),
        )
            .into_response());
    }

    if new_setting.mute_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.mute_action_duration_type > DURATION_TYPE_DAYS
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for mute action duration type!"),
        )
            .into_response());
    }

    if new_setting.warn_threshold < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for warn threshold!"),
        )
            .into_response());
    }

    if new_setting.warn_action < ACTION_NOTHING || new_setting.warn_action > ACTION_HARDBAN {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for warn action!"),
        )
            .into_response());
    }

    if new_setting.warn_action_duration < 0 {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for warn action duration!"),
        )
            .into_response());
    }

    if new_setting.warn_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.warn_action_duration_type > DURATION_TYPE_DAYS
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for warn action duration type!"),
        )
            .into_response());
    }

    if new_setting.privacy_settings < PRIVACY_EVERYONE
        || new_setting.privacy_settings > PRIVACY_ADMIN_ONLY
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for moderator log privacy!"),
        )
            .into_response());
    }

    if new_setting.privacy_mod_log < PRIVACY_EVERYONE
        || new_setting.privacy_mod_log > PRIVACY_ADMIN_ONLY
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Invalid value for moderator log privacy!"),
        )
            .into_response());
    }

    if new_setting.guild.id != guild_id.to_string() {
        return Err((StatusCode::BAD_REQUEST, Json("Invalid guild id!")).into_response());
    }

    let setting_service =
        extract_service::<SettingService>(&services).map_err(|err| err.into_response())?;

    setting_service
        .update_setting(
            guild_id,
            Setting {
                guild_id: guild_id.0.get() as i64,
                mod_log: new_setting.mod_log,
                mod_log_channel_id,
                holding_room: new_setting.holding_room,
                holding_room_role_id,
                holding_room_minutes: new_setting.holding_room_minutes,
                invite_link_remover: new_setting.invite_link_remover,
                welcome_message: new_setting.welcome_message,
                message: new_setting.message.clone(),
                welcome_message_channel_id,
                join_captcha: new_setting.join_captcha,
                word_filter: new_setting.word_filter,
                word_filter_blocklist: new_setting.word_filter_blocklist.clone(),
                word_filter_level: new_setting.word_filter_level,
                word_filter_action: new_setting.word_filter_action,
                word_filter_action_duration: new_setting.word_filter_action_duration,
                word_filter_action_duration_type: new_setting.word_filter_action_duration_type,
                invite_link_remover_action: new_setting.invite_link_remover_action,
                invite_link_remover_action_duration: new_setting
                    .invite_link_remover_action_duration,
                invite_link_remover_action_duration_type: new_setting
                    .invite_link_remover_action_duration_type,
                privacy_settings: new_setting.privacy_settings,
                privacy_mod_log: new_setting.privacy_mod_log,
                softban_threshold: new_setting.softban_threshold,
                softban_action: new_setting.softban_action,
                softban_action_duration: new_setting.softban_action_duration,
                softban_action_duration_type: new_setting.softban_action_duration_type,
                kick_threshold: new_setting.kick_threshold,
                kick_action: new_setting.kick_action,
                kick_action_duration: new_setting.kick_action_duration,
                kick_action_duration_type: new_setting.kick_action_duration_type,
                mute_threshold: new_setting.mute_threshold,
                mute_action: new_setting.mute_action,
                mute_action_duration: new_setting.mute_action_duration,
                mute_action_duration_type: new_setting.mute_action_duration_type,
                warn_threshold: new_setting.warn_threshold,
                warn_action: new_setting.warn_action,
                warn_action_duration: new_setting.warn_action_duration,
                warn_action_duration_type: new_setting.warn_action_duration_type,
                mods_can_edit_tags: new_setting.mods_can_edit_tags,
                spam_filter: new_setting.spam_filter,
            },
        )
        .await;

    Ok(())
}

// /guilds/:guild_id/settings
pub async fn reset_setting(
    State(services): State<Arc<Services>>,
    SettingEndpointParams(guild_id): SettingEndpointParams,
) -> Result<(), Response> {
    let setting_service =
        extract_service::<SettingService>(&services).map_err(|err| err.into_response())?;

    setting_service.reset_setting(guild_id).await;

    Ok(())
}
