use std::sync::Arc;

use crate::util::now;
use sqlx::{Error, PgPool, Row};

#[derive(sqlx::FromRow)]
pub struct Ban {
    pub id: i32,
    pub user_id: i64,
    pub moderator_user_id: i64,
    pub guild_id: i64,
    pub ban_time: i64,
    pub expire_time: i64, // expiretime
    pub reason: String,
    pub expires: bool,
    pub unbanned: bool,
}

pub struct BansRepository(pub Arc<PgPool>);

impl BansRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/bans/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!("sql/bans/create_ban_time_index.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_ban(&self, id: i32) -> Result<Option<Ban>, Error> {
        Ok(
            sqlx::query_as::<_, Ban>(include_str!("sql/bans/select_ban_with_id.sql"))
                .bind(id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_bans(&self, guild_id: i64, page: u32) -> Result<Vec<Ban>, Error> {
        Ok(
            sqlx::query_as::<_, Ban>(include_str!("sql/bans/select_guild_bans_paginated.sql"))
                .bind(guild_id)
                .bind((page - 1) * 10)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_ban_count(&self, guild_id: i64) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!("sql/bans/count_guild_bans.sql"))
            .bind(guild_id)
            .fetch_one(&*self.0)
            .await?
            .get(0))
    }

    pub async fn fetch_expired_bans(&self) -> Result<Vec<Ban>, Error> {
        Ok(
            sqlx::query_as::<_, Ban>(include_str!("sql/bans/select_expired_guild_bans.sql"))
                .bind(now() as i64)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_last_guild_ban(&self, guild_id: i64) -> Result<Option<Ban>, Error> {
        Ok(
            sqlx::query_as::<_, Ban>(include_str!("sql/bans/select_last_guild_ban.sql"))
                .bind(guild_id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn insert_ban(&self, ban: Ban) -> Result<Ban, Error> {
        Ok(
            sqlx::query_as::<_, Ban>(include_str!("sql/bans/insert_entity.sql"))
                .bind(ban.user_id)
                .bind(ban.moderator_user_id)
                .bind(ban.guild_id)
                .bind(ban.ban_time)
                .bind(ban.expire_time)
                .bind(ban.reason)
                .bind(ban.expires)
                .bind(ban.unbanned)
                .fetch_one(&*self.0)
                .await?,
        )
    }

    pub async fn update_ban(&self, ban: Ban) -> Result<(), Error> {
        sqlx::query(include_str!("sql/bans/update_entity.sql"))
            .bind(ban.id)
            .bind(ban.user_id)
            .bind(ban.moderator_user_id)
            .bind(ban.guild_id)
            .bind(ban.ban_time)
            .bind(ban.expire_time)
            .bind(ban.reason)
            .bind(ban.expires)
            .bind(ban.unbanned)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn invalidate_previous_user_bans(
        &self,
        guild_id: i64,
        user_id: i64,
    ) -> Result<(), Error> {
        sqlx::query(include_str!(
            "sql/bans/invalidate_previous_bans_of_user.sql"
        ))
        .bind(guild_id)
        .bind(user_id)
        .execute(&*self.0)
        .await?;

        Ok(())
    }

    pub async fn invalidate_ban(&self, id: i32) -> Result<(), Error> {
        sqlx::query(include_str!("sql/bans/invalidate_entity.sql"))
            .bind(id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
