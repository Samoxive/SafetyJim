use std::num::NonZeroU64;
use actix_web::{delete, get, post, web, HttpResponse, Responder};
use serenity::model::id::{ChannelId, GuildId, RoleId};
use typemap_rev::TypeMap;

use crate::database::settings::{
    Setting, ACTION_HARDBAN, ACTION_NOTHING, DURATION_TYPE_DAYS, DURATION_TYPE_SECONDS,
    PRIVACY_ADMIN_ONLY, PRIVACY_EVERYONE,
    WORD_FILTER_LEVEL_HIGH, WORD_FILTER_LEVEL_LOW,
};
use crate::server::model::channel::ChannelModel;
use crate::server::model::guild::GuildModel;
use crate::server::model::role::RoleModel;
use crate::server::model::setting::SettingModel;
use crate::server::{
    apply_private_endpoint_fetch_checks, apply_setting_update_checks, PrivateEndpointKind,
};
use crate::service::guild::GuildService;
use crate::service::setting::SettingService;
use crate::Config;

#[get("/guilds/{guild_id}/settings")]
pub async fn get_setting(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    guild_id: web::Path<NonZeroU64>,
) -> impl Responder {
    let guild_id = GuildId(guild_id.into_inner());

    if let Err(response) = apply_private_endpoint_fetch_checks(
        &config,
        &services,
        &req,
        guild_id,
        PrivateEndpointKind::Settings,
    )
    .await
    {
        return response;
    }

    let guild_service = if let Some(service) = services.get::<GuildService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    // TODO(sam): front end doesn't really use this data, remove?
    let guild = match guild_service.get_guild(guild_id).await {
        Ok(guild) => guild,
        Err(_) => {
            return HttpResponse::BadRequest()
                .json("Failed to fetch guild data, is Jim in this server?");
        }
    };

    let channels = match guild_service.get_channels(guild_id).await {
        Ok(channels) => channels,
        Err(_) => {
            return HttpResponse::BadRequest()
                .json("Failed to fetch guild data, is Jim in this server?");
        }
    };

    let roles = match guild_service.get_roles(guild_id).await {
        Ok(roles) => roles,
        Err(_) => {
            return HttpResponse::BadRequest()
                .json("Failed to fetch guild data, is Jim in this server?");
        }
    };

    let setting_service = if let Some(service) = services.get::<SettingService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let setting = setting_service.get_setting(guild_id).await;

    let mod_log_channel = NonZeroU64::new(setting.mod_log_channel_id as u64)
        .map(ChannelId)
        .and_then(|channel_id| channels.get(&channel_id).map(|channel| (channel_id, channel)))
        .map(|(id, channel)| ChannelModel::from_guild_channel(id, channel));

    let holding_room_role = setting.holding_room_role_id
        .and_then(|id| NonZeroU64::new(id as u64))
        .map(RoleId)
        .and_then(|role_id| roles.get(&role_id).map(|role| (role_id, role)))
        .map(|(role_id, role)| RoleModel::from_role(role_id, role));

    let welcome_channel = NonZeroU64::new(setting.welcome_message_channel_id as u64)
        .map(ChannelId)
        .and_then(|channel_id| channels.get(&channel_id).map(|channel| (channel_id, channel)))
        .map(|(id, channel)| ChannelModel::from_guild_channel(id, channel));

    HttpResponse::Ok().json(SettingModel {
        guild: GuildModel::from_cached_guild(guild_id, &*guild),
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
    })
}

#[post("/guilds/{guild_id}/settings")]
pub async fn update_setting(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    guild_id: web::Path<NonZeroU64>,
    mut new_setting: web::Json<SettingModel>,
) -> impl Responder {
    let guild_id = GuildId(guild_id.into_inner());

    if let Err(response) = apply_setting_update_checks(&config, &services, &req, guild_id).await {
        return response;
    }

    let guild_service = if let Some(service) = services.get::<GuildService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let channels = match guild_service.get_channels(guild_id).await {
        Ok(channels) => channels,
        Err(_) => {
            return HttpResponse::BadRequest()
                .json("Failed to fetch guild data, is Jim in this server?");
        }
    };

    let roles = match guild_service.get_roles(guild_id).await {
        Ok(roles) => roles,
        Err(_) => {
            return HttpResponse::BadRequest()
                .json("Failed to fetch guild data, is Jim in this server?");
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
                return HttpResponse::BadRequest()
                    .json("Selected moderator log channel id is invalid!");
            }
        };

        if channels.get(&channel_id).is_none() {
            return HttpResponse::BadRequest()
                .json("Selected moderator log channel doesn't exist!");
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
                    return HttpResponse::BadRequest()
                        .json("Selected welcome message channel id is invalid!");
                }
            };

            if channels.get(&channel_id).is_none() {
                return HttpResponse::BadRequest()
                    .json("Selected welcome message channel doesn't exist!");
            }

            channel_id.get() as i64
        } else {
            0
        };

    let holding_room_role_id = if let Some(role) = new_setting.holding_room_role.as_ref() {
        let role_id = match role.id.parse::<NonZeroU64>() {
            Ok(id) => RoleId(id),
            Err(_) => {
                return HttpResponse::BadRequest()
                    .json("Selected holding room role id is invalid!");
            }
        };

        if roles.get(&role_id).is_none() {
            return HttpResponse::BadRequest().json("Selected holding room role doesn't exist!");
        }

        Some(role_id.get() as i64)
    } else {
        if new_setting.join_captcha || new_setting.holding_room {
            return HttpResponse::BadRequest().json("You can't enable join captcha or holding room without setting a holding room role!");
        }

        None
    };

    if new_setting.join_captcha && new_setting.holding_room {
        return HttpResponse::BadRequest()
            .json("You can't enable both holding room and join captcha at the same time!");
    }

    if new_setting.holding_room_minutes < 0 {
        return HttpResponse::BadRequest().json("Holding room minutes cannot be negative!");
    }

    if new_setting.message.is_empty() {
        return HttpResponse::BadRequest().json("Welcome message cannot be empty!");
    } else if new_setting.message.len() >= 1750 {
        return HttpResponse::BadRequest().json("Welcome message cannot be too long!");
    }

    if let Some(blocklist) = new_setting.word_filter_blocklist.as_ref() {
        if blocklist.len() > 2000 {
            return HttpResponse::BadRequest().json("Word filter blocklist cannot be too long!");
        }
    }

    if new_setting.word_filter_level != WORD_FILTER_LEVEL_LOW
        && new_setting.word_filter_level != WORD_FILTER_LEVEL_HIGH
    {
        return HttpResponse::BadRequest().json("Invalid value for word filter level!");
    }

    if new_setting.word_filter_action < ACTION_NOTHING
        || new_setting.word_filter_action > ACTION_HARDBAN
    {
        return HttpResponse::BadRequest().json("Invalid value for word filter action!");
    }

    if new_setting.word_filter_action_duration < 0 {
        return HttpResponse::BadRequest().json("Invalid value for word filter action duration!");
    }

    if new_setting.word_filter_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.word_filter_action_duration_type > DURATION_TYPE_DAYS
    {
        return HttpResponse::BadRequest()
            .json("Invalid value for word filter action duration type!");
    }

    if new_setting.invite_link_remover_action < ACTION_NOTHING
        || new_setting.invite_link_remover_action > ACTION_HARDBAN
    {
        return HttpResponse::BadRequest().json("Invalid value for invite link remover action!");
    }

    if new_setting.invite_link_remover_action_duration < 0 {
        return HttpResponse::BadRequest()
            .json("Invalid value for invite link remover action duration!");
    }

    if new_setting.invite_link_remover_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.invite_link_remover_action_duration_type > DURATION_TYPE_DAYS
    {
        return HttpResponse::BadRequest()
            .json("Invalid value for invite link remover action duration type!");
    }

    if new_setting.softban_threshold < 0 {
        return HttpResponse::BadRequest().json("Invalid value for softban threshold!");
    }

    if new_setting.softban_action < ACTION_NOTHING || new_setting.softban_action > ACTION_HARDBAN {
        return HttpResponse::BadRequest().json("Invalid value for softban action!");
    }

    if new_setting.softban_action_duration < 0 {
        return HttpResponse::BadRequest().json("Invalid value for softban action duration!");
    }

    if new_setting.softban_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.softban_action_duration_type > DURATION_TYPE_DAYS
    {
        return HttpResponse::BadRequest().json("Invalid value for softban action duration type!");
    }

    if new_setting.kick_threshold < 0 {
        return HttpResponse::BadRequest().json("Invalid value for kick threshold!");
    }

    if new_setting.kick_action < ACTION_NOTHING || new_setting.kick_action > ACTION_HARDBAN {
        return HttpResponse::BadRequest().json("Invalid value for kick action!");
    }

    if new_setting.kick_action_duration < 0 {
        return HttpResponse::BadRequest().json("Invalid value for kick action duration!");
    }

    if new_setting.kick_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.kick_action_duration_type > DURATION_TYPE_DAYS
    {
        return HttpResponse::BadRequest().json("Invalid value for kick action duration type!");
    }

    if new_setting.mute_threshold < 0 {
        return HttpResponse::BadRequest().json("Invalid value for mute threshold!");
    }

    if new_setting.mute_action < ACTION_NOTHING || new_setting.mute_action > ACTION_HARDBAN {
        return HttpResponse::BadRequest().json("Invalid value for mute action!");
    }

    if new_setting.mute_action_duration < 0 {
        return HttpResponse::BadRequest().json("Invalid value for mute action duration!");
    }

    if new_setting.mute_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.mute_action_duration_type > DURATION_TYPE_DAYS
    {
        return HttpResponse::BadRequest().json("Invalid value for mute action duration type!");
    }

    if new_setting.warn_threshold < 0 {
        return HttpResponse::BadRequest().json("Invalid value for warn threshold!");
    }

    if new_setting.warn_action < ACTION_NOTHING || new_setting.warn_action > ACTION_HARDBAN {
        return HttpResponse::BadRequest().json("Invalid value for warn action!");
    }

    if new_setting.warn_action_duration < 0 {
        return HttpResponse::BadRequest().json("Invalid value for warn action duration!");
    }

    if new_setting.warn_action_duration_type < DURATION_TYPE_SECONDS
        || new_setting.warn_action_duration_type > DURATION_TYPE_DAYS
    {
        return HttpResponse::BadRequest().json("Invalid value for warn action duration type!");
    }

    if new_setting.privacy_settings < PRIVACY_EVERYONE
        || new_setting.privacy_settings > PRIVACY_ADMIN_ONLY
    {
        return HttpResponse::BadRequest().json("Invalid value for moderator log privacy!");
    }

    if new_setting.privacy_mod_log < PRIVACY_EVERYONE
        || new_setting.privacy_mod_log > PRIVACY_ADMIN_ONLY
    {
        return HttpResponse::BadRequest().json("Invalid value for moderator log privacy!");
    }

    if new_setting.guild.id != guild_id.to_string() {
        return HttpResponse::BadRequest().json("Invalid guild id!");
    }

    let setting_service = if let Some(service) = services.get::<SettingService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

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

    HttpResponse::Ok().finish()
}

#[delete("/guilds/{guild_id}/settings")]
pub async fn reset_setting(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    guild_id: web::Path<NonZeroU64>,
) -> impl Responder {
    let guild_id = GuildId(guild_id.into_inner());

    if let Err(response) = apply_setting_update_checks(&config, &services, &req, guild_id).await {
        return response;
    }

    let setting_service = if let Some(service) = services.get::<SettingService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    setting_service.reset_setting(guild_id).await;

    HttpResponse::Ok().finish()
}
