use typemap_rev::TypeMapKey;

use crate::database::reminders::{Reminder, RemindersRepository};
use crate::util::now;
use serenity::model::id::{ChannelId, GuildId};
use serenity::model::user::User;
use std::time::Duration;
use tracing::error;

impl TypeMapKey for ReminderService {
    type Value = ReminderService;
}

pub struct ReminderService {
    pub repository: RemindersRepository,
}

pub enum CreateReminderFailure {
    Unknown,
}

impl ReminderService {
    pub async fn fetch_expired_reminders(&self) -> Vec<Reminder> {
        self.repository
            .fetch_expired_reminders()
            .await
            .map_err(|err| {
                error!("failed to fetch expired reminders {:?}", err);
                err
            })
            .ok()
            .unwrap_or_else(Vec::new)
    }

    pub async fn create_reminder(
        &self,
        guild_id: GuildId,
        channel_id: ChannelId,
        user: &User,
        duration: Option<Duration>,
        message: String,
    ) -> Result<(), CreateReminderFailure> {
        let now = now();
        let remind_time = duration
            .map(|duration| now + duration.as_secs())
            .unwrap_or_else(|| now + 1000 * 60 * 60 * 24);

        let reminder = Reminder {
            id: 0,
            user_id: user.id.0 as i64,
            channel_id: channel_id.0 as i64,
            guild_id: guild_id.0 as i64,
            create_time: now as i64,
            remind_time: remind_time as i64,
            reminded: false,
            message,
        };

        return match self.repository.insert_reminder(reminder).await {
            Ok(_) => Ok(()),
            Err(err) => {
                error!("failed to insert reminder {:?}", err);
                Err(CreateReminderFailure::Unknown)
            }
        };
    }

    pub async fn invalidate_reminder(&self, id: i32) {
        let _ = self
            .repository
            .invalidate_reminder(id)
            .await
            .map_err(|err| {
                error!("failed to invalidate reminder {:?}", err);
                err
            });
    }
}
