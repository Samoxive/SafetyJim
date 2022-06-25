use std::sync::Arc;

use sqlx::{Error, PgPool};

use crate::util::now;

#[derive(sqlx::FromRow, Debug)]
pub struct Reminder {
    pub id: i32,
    pub user_id: i64,
    pub channel_id: i64,
    pub guild_id: i64,
    pub create_time: i64,
    pub remind_time: i64,
    pub reminded: bool,
    pub message: String,
}

pub struct RemindersRepository(pub Arc<PgPool>);

impl RemindersRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/reminders/create_table.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_expired_reminders(&self) -> Result<Vec<Reminder>, Error> {
        sqlx::query_as::<_, Reminder>(include_str!("sql/reminders/select_expired_reminders.sql"))
            .bind(now() as i64)
            .fetch_all(&*self.0)
            .await
    }

    pub async fn insert_reminder(&self, reminder: Reminder) -> Result<Reminder, Error> {
        sqlx::query_as::<_, Reminder>(include_str!("sql/reminders/insert_entity.sql"))
            .bind(reminder.user_id)
            .bind(reminder.channel_id)
            .bind(reminder.guild_id)
            .bind(reminder.create_time)
            .bind(reminder.remind_time)
            .bind(reminder.reminded)
            .bind(reminder.message)
            .fetch_one(&*self.0)
            .await
    }

    pub async fn _update_reminder(&self, reminder: Reminder) -> Result<(), Error> {
        sqlx::query(include_str!("sql/reminders/update_entity.sql"))
            .bind(reminder.id)
            .bind(reminder.user_id)
            .bind(reminder.channel_id)
            .bind(reminder.guild_id)
            .bind(reminder.create_time)
            .bind(reminder.remind_time)
            .bind(reminder.reminded)
            .bind(reminder.message)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn invalidate_reminder(&self, id: i32) -> Result<(), Error> {
        sqlx::query(include_str!("sql/reminders/invalidate_entity.sql"))
            .bind(id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
