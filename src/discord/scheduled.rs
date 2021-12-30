use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::service::ban::BanService;
use crate::service::guild::GuildService;
use crate::service::join::JoinService;
use crate::service::mute::MuteService;
use crate::service::reminder::ReminderService;
use crate::service::setting::SettingService;
use chrono::{TimeZone, Utc};
use serenity::builder::CreateEmbed;
use serenity::http::Http;
use serenity::model::id::{ChannelId, GuildId, UserId};
use serenity::prelude::Mentionable;
use std::sync::Arc;
use std::time::Duration;
use tracing::error;
use typemap_rev::TypeMap;

pub fn run_scheduled_tasks(http: Arc<Http>, services: Arc<TypeMap>) {
    let http_1 = http.clone();
    let services_1 = services.clone();
    let _ = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(5));
        loop {
            interval.tick().await;
            allow_users(&*http_1, &*services_1).await;
        }
    });

    let http_2 = http.clone();
    let services_2 = services.clone();
    let _ = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(10));
        loop {
            interval.tick().await;
            unmute_users(&*http_2, &*services_2).await;
        }
    });

    let http_3 = http.clone();
    let services_3 = services.clone();
    let _ = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(30));
        loop {
            interval.tick().await;
            unban_users(&*http_3, &*services_3).await;
        }
    });

    let _ = tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(5));
        loop {
            interval.tick().await;
            remind_reminders(&*http, &*services).await;
        }
    });
}

pub async fn allow_users(http: &Http, services: &TypeMap) {
    let join_service = if let Some(service) = services.get::<JoinService>() {
        service
    } else {
        return;
    };

    let setting_service = if let Some(service) = services.get::<SettingService>() {
        service
    } else {
        return;
    };

    let expired_joins = join_service.get_expired_joins().await;
    for expired_join in expired_joins {
        let setting = setting_service
            .get_setting(GuildId(expired_join.guild_id as u64))
            .await;
        if setting.holding_room {
            if let Some(holding_room_role_id) = setting.holding_room_role_id {
                let _ = http
                    .add_member_role(
                        expired_join.guild_id as u64,
                        expired_join.user_id as u64,
                        holding_room_role_id as u64,
                        Some("Taking member out of holding room because duration expired"),
                    )
                    .await
                    .map_err(|err| {
                        error!("failed to issue discord member role add {}", err);
                        err
                    });
            }
        }

        join_service.invalidate_join(expired_join.id).await;
    }
}

pub async fn unmute_users(http: &Http, services: &TypeMap) {
    let mute_service = if let Some(service) = services.get::<MuteService>() {
        service
    } else {
        return;
    };

    let guild_service = if let Some(service) = services.get::<GuildService>() {
        service
    } else {
        return;
    };

    let expired_mutes = mute_service.fetch_expired_mutes().await;
    for expired_mute in expired_mutes {
        if let Some(muted_role_id) = guild_service
            .get_roles(GuildId(expired_mute.guild_id as u64))
            .await
            .map(|roles| {
                roles
                    .iter()
                    .find(|(_, role)| role.name == "Muted")
                    .map(|(id, _)| *id)
            })
            .ok()
            .flatten()
        {
            let _ = http
                .remove_member_role(
                    expired_mute.guild_id as u64,
                    expired_mute.user_id as u64,
                    muted_role_id.0,
                    Some("Unmuting member because duration expired"),
                )
                .await
                .map_err(|err| {
                    error!("failed to issue discord member role remove {}", err);
                    err
                });
        }

        mute_service.invalidate_mute(expired_mute.id).await;
    }
}

pub async fn unban_users(http: &Http, services: &TypeMap) {
    let ban_service = if let Some(service) = services.get::<BanService>() {
        service
    } else {
        return;
    };

    let expired_bans = ban_service.fetch_expired_bans().await;
    for expired_ban in expired_bans {
        let _ = http
            .remove_ban(
                expired_ban.guild_id as u64,
                expired_ban.user_id as u64,
                Some("Unbanning member because duration expired"),
            )
            .await
            .map_err(|err| {
                error!("failed to issue discord unban {}", err);
                err
            });

        ban_service.invalidate_ban(expired_ban.id).await;
    }
}

pub async fn remind_reminders(http: &Http, services: &TypeMap) {
    let reminder_service = if let Some(service) = services.get::<ReminderService>() {
        service
    } else {
        return;
    };

    let guild_service = if let Some(service) = services.get::<GuildService>() {
        service
    } else {
        return;
    };

    let expired_reminders = reminder_service.fetch_expired_reminders().await;
    for expired_reminder in expired_reminders {
        let mut embed = CreateEmbed::default();
        embed.title(format!("Reminder - #{}", expired_reminder.id));
        embed.description(expired_reminder.message);
        embed.author(|author| author.name("Safety Jim").icon_url(AVATAR_URL));
        embed.footer(|footer| footer.text("Reminder set on"));
        embed.timestamp(
            Utc.timestamp(expired_reminder.create_time as i64, 0)
                .to_rfc3339(),
        );
        embed.colour(EMBED_COLOR);

        let guild_id = GuildId(expired_reminder.guild_id as u64);
        let channel_id = ChannelId(expired_reminder.channel_id as u64);
        let user_id = UserId(expired_reminder.user_id as u64);

        let mut is_dm_required = false;
        if guild_service.get_member(guild_id, user_id).await.is_ok() {
            // user is in the guild, send reminder to guild channel
            let channel_message_result = channel_id
                .send_message(http, |message| {
                    message.content(user_id.mention()).set_embed(embed.clone())
                })
                .await
                .map_err(|err| {
                    error!("failed to send channel message {}", err);
                    err
                });
            if channel_message_result.is_err() {
                is_dm_required = true;
            }
        } else {
            is_dm_required = true;
        }

        if is_dm_required {
            let dm_channel_result = user_id
                .create_dm_channel(http)
                .await
                .map_err(|err| {
                    error!("failed to create DM channel {}", err);
                    err
                });
            if let Ok(dm_channel) = dm_channel_result {
                let _ = dm_channel
                    .send_message(http, |message| {
                        message.content(user_id.mention()).set_embed(embed)
                    })
                    .await
                    .map_err(|err| {
                        error!("failed to send DM {}", err);
                        err
                    });
            }
        }

        reminder_service
            .invalidate_reminder(expired_reminder.id)
            .await;
    }
}
