use std::num::NonZeroU64;
use std::sync::Arc;
use std::time::Duration;
use serenity::all::Mentionable;

use serenity::builder::{CreateEmbed, CreateEmbedAuthor, CreateEmbedFooter, CreateMessage};
use serenity::http::Http;
use serenity::model::id::{ChannelId, GuildId, RoleId, UserId};
use serenity::model::Timestamp;
use tokio::select;
use tokio::time::interval;
use tracing::{error, warn};

use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::service::ban::BanService;
use crate::service::guild::GuildService;
use crate::service::join::JoinService;
use crate::service::mute::MuteService;
use crate::service::reminder::ReminderService;
use crate::service::setting::SettingService;
use crate::service::Services;
use crate::util::Shutdown;

pub fn run_scheduled_tasks(http: Arc<Http>, services: Arc<Services>, shutdown: Shutdown) {
    let http_1 = http.clone();
    let services_1 = services.clone();
    let mut receiver_1 = shutdown.subscribe();
    drop(tokio::spawn(async move {
        let mut interval = interval(Duration::from_secs(5));
        loop {
            select! {
                _ = interval.tick() => {}
                _ = receiver_1.recv() => {
                    return;
                }
            };
            allow_users(&http_1, &services_1).await;
        }
    }));

    let http_2 = http.clone();
    let services_2 = services.clone();
    let mut receiver_2 = shutdown.subscribe();
    drop(tokio::spawn(async move {
        let mut interval = interval(Duration::from_secs(10));
        loop {
            select! {
                _ = interval.tick() => {}
                _ = receiver_2.recv() => {
                    return;
                }
            };

            unmute_users(&http_2, &services_2).await;
        }
    }));

    let http_3 = http.clone();
    let services_3 = services.clone();
    let mut receiver_3 = shutdown.subscribe();
    drop(tokio::spawn(async move {
        let mut interval = interval(Duration::from_secs(30));
        loop {
            select! {
                _ = interval.tick() => {}
                _ = receiver_3.recv() => {
                    return;
                }
            };
            unban_users(&http_3, &services_3).await;
        }
    }));

    let mut receiver_4 = shutdown.subscribe();
    drop(tokio::spawn(async move {
        let mut interval = interval(Duration::from_secs(5));
        loop {
            select! {
                _ = interval.tick() => {}
                _ = receiver_4.recv() => {
                    return;
                }
            };
            remind_reminders(&http, &services).await;
        }
    }));
}

pub async fn allow_users(http: &Http, services: &Services) {
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
        let guild_id = if let Some(id) = NonZeroU64::new(expired_join.guild_id as u64) {
            GuildId::new(id.get())
        } else {
            warn!(
                "found expired join with invalid guild id! {:?}",
                expired_join
            );
            join_service.invalidate_join(expired_join.id).await;
            continue;
        };

        let setting = setting_service.get_setting(guild_id).await;

        if setting.holding_room {
            if let Some(holding_room_role_id) = setting.holding_room_role_id {
                // these aren't likely to be zero but we need sanity checks to avoid panic
                let user_id = if let Some(id) = NonZeroU64::new(expired_join.user_id as u64) {
                    UserId::new(id.get())
                } else {
                    warn!(
                        "found expired join with invalid user id! {:?}",
                        expired_join
                    );
                    join_service.invalidate_join(expired_join.id).await;
                    continue;
                };

                let role_id = if let Some(id) = NonZeroU64::new(holding_room_role_id as u64) {
                    RoleId::new(id.get())
                } else {
                    warn!(
                        "found expired join in guild with invalid role id! {:?}",
                        expired_join
                    );
                    join_service.invalidate_join(expired_join.id).await;
                    continue;
                };

                let _ = http
                    .add_member_role(
                        guild_id,
                        user_id,
                        role_id,
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

pub async fn unmute_users(http: &Http, services: &Services) {
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
        let guild_id = if let Some(id) = NonZeroU64::new(expired_mute.guild_id as u64) {
            GuildId::new(id.get())
        } else {
            warn!(
                "found expired mute with invalid guild id! {:?}",
                expired_mute
            );
            mute_service.invalidate_mute(expired_mute.id).await;
            continue;
        };

        if let Some(muted_role_id) = guild_service
            .get_roles(guild_id)
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
            // these aren't likely to be zero but we need sanity checks to avoid panic
            let user_id = if let Some(id) = NonZeroU64::new(expired_mute.user_id as u64) {
                UserId::new(id.get())
            } else {
                warn!(
                    "found expired mute with invalid user id! {:?}",
                    expired_mute
                );
                mute_service.invalidate_mute(expired_mute.id).await;
                continue;
            };

            let _ = http
                .remove_member_role(
                    guild_id,
                    user_id,
                    muted_role_id,
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

pub async fn unban_users(http: &Http, services: &Services) {
    let ban_service = if let Some(service) = services.get::<BanService>() {
        service
    } else {
        return;
    };

    let expired_bans = ban_service.fetch_expired_bans().await;
    for expired_ban in expired_bans {
        // these aren't likely to be zero but we need sanity checks to avoid panic
        let guild_id = if let Some(id) = NonZeroU64::new(expired_ban.guild_id as u64) {
            GuildId::new(id.get())
        } else {
            warn!("found expired ban with invalid guild id! {:?}", expired_ban);
            ban_service.invalidate_ban(expired_ban.id).await;
            continue;
        };

        let user_id = if let Some(id) = NonZeroU64::new(expired_ban.user_id as u64) {
            UserId::new(id.get())
        } else {
            warn!("found expired ban with invalid user id! {:?}", expired_ban);
            ban_service.invalidate_ban(expired_ban.id).await;
            continue;
        };

        let _ = http
            .remove_ban(
                guild_id,
                user_id,
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

pub async fn remind_reminders(http: &Http, services: &Services) {
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
        let timestamp = match Timestamp::from_unix_timestamp(expired_reminder.create_time) {
            Ok(t) => t,
            Err(_) => {
                warn!(
                    "found expired reminder with invalid expiration time! {:?}",
                    expired_reminder
                );
                reminder_service
                    .invalidate_reminder(expired_reminder.id)
                    .await;
                continue;
            }
        };
        let embed = CreateEmbed::default()
            .title(format!("Reminder - #{}", expired_reminder.id))
            .description(&expired_reminder.message)
            .author(CreateEmbedAuthor::new("Safety Jim").icon_url(AVATAR_URL))
            .footer(CreateEmbedFooter::new("Reminder set on"))
            .timestamp(timestamp)
            .colour(EMBED_COLOR);

        let guild_id = if let Some(id) = NonZeroU64::new(expired_reminder.guild_id as u64) {
            GuildId::new(id.get())
        } else {
            warn!(
                "found expired reminder with invalid guild id! {:?}",
                expired_reminder
            );
            reminder_service
                .invalidate_reminder(expired_reminder.id)
                .await;
            continue;
        };

        let channel_id = if let Some(id) = NonZeroU64::new(expired_reminder.channel_id as u64) {
            ChannelId::new(id.get())
        } else {
            warn!(
                "found expired reminder with invalid channel id! {:?}",
                expired_reminder
            );
            reminder_service
                .invalidate_reminder(expired_reminder.id)
                .await;
            continue;
        };

        let user_id = if let Some(id) = NonZeroU64::new(expired_reminder.user_id as u64) {
            UserId::new(id.get())
        } else {
            warn!(
                "found expired reminder with invalid user id! {:?}",
                expired_reminder
            );
            reminder_service
                .invalidate_reminder(expired_reminder.id)
                .await;
            continue;
        };

        let mut is_dm_required = false;
        if guild_service.get_member(guild_id, user_id).await.is_ok() {
            // user is in the guild, send reminder to guild channel
            let message = CreateMessage::default()
                .content(user_id.mention().to_string())
                .add_embed(embed.clone());

            let channel_message_result =
                channel_id.send_message(http, message).await.map_err(|err| {
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
            let dm_channel_result = user_id.create_dm_channel(http).await.map_err(|err| {
                error!("failed to create DM channel {}", err);
                err
            });
            if let Ok(dm_channel) = dm_channel_result {
                let message = CreateMessage::default()
                    .content(user_id.mention().to_string())
                    .add_embed(embed);

                let _ = dm_channel.send_message(http, message).await.map_err(|err| {
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
