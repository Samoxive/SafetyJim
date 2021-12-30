use std::sync::Arc;

use crate::util::now;
use sqlx::{Error, PgPool, Row};

#[derive(sqlx::FromRow)]
pub struct Mute {
    pub id: i32,
    pub user_id: i64,
    pub moderator_user_id: i64,
    pub guild_id: i64,
    pub mute_time: i64,
    pub expire_time: i64,
    pub reason: String,
    pub expires: bool,
    pub unmuted: bool,
    pub pardoned: bool,
}

pub struct MutesRepository(pub Arc<PgPool>);

impl MutesRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/mutes/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!("sql/mutes/create_mute_time_index.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_mute(&self, id: i32) -> Result<Option<Mute>, Error> {
        Ok(
            sqlx::query_as::<_, Mute>(include_str!("sql/mutes/select_mute_with_id.sql"))
                .bind(id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_mutes(&self, guild_id: i64, page: u32) -> Result<Vec<Mute>, Error> {
        Ok(
            sqlx::query_as::<_, Mute>(include_str!("sql/mutes/select_guild_mutes_paginated.sql"))
                .bind(guild_id)
                .bind((page - 1) * 10)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_mute_count(&self, guild_id: i64) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!("sql/mutes/count_guild_mutes.sql"))
            .bind(guild_id)
            .fetch_one(&*self.0)
            .await?
            .get(0))
    }

    pub async fn fetch_expired_mutes(&self) -> Result<Vec<Mute>, Error> {
        Ok(
            sqlx::query_as::<_, Mute>(include_str!("sql/mutes/select_expired_guild_mutes.sql"))
                .bind(now() as i64)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_valid_mutes(&self, guild_id: i64, user_id: i64) -> Result<Vec<Mute>, Error> {
        Ok(
            sqlx::query_as::<_, Mute>(include_str!("sql/mutes/select_valid_guild_user_mutes.sql"))
                .bind(guild_id)
                .bind(user_id)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_actionable_mute_count(
        &self,
        guild_id: i64,
        user_id: i64,
    ) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!(
            "sql/mutes/count_actionable_guild_user_mutes.sql"
        ))
        .bind(guild_id)
        .bind(user_id)
        .fetch_one(&*self.0)
        .await?
        .get(0))
    }

    pub async fn insert_mute(&self, mute: Mute) -> Result<Mute, Error> {
        Ok(
            sqlx::query_as::<_, Mute>(include_str!("sql/mutes/insert_entity.sql"))
                .bind(mute.user_id)
                .bind(mute.moderator_user_id)
                .bind(mute.guild_id)
                .bind(mute.mute_time)
                .bind(mute.expire_time)
                .bind(mute.reason)
                .bind(mute.expires)
                .bind(mute.unmuted)
                .bind(mute.pardoned)
                .fetch_one(&*self.0)
                .await?,
        )
    }

    pub async fn update_mute(&self, mute: Mute) -> Result<(), Error> {
        sqlx::query(include_str!("sql/mutes/update_entity.sql"))
            .bind(mute.id)
            .bind(mute.user_id)
            .bind(mute.moderator_user_id)
            .bind(mute.guild_id)
            .bind(mute.mute_time)
            .bind(mute.expire_time)
            .bind(mute.reason)
            .bind(mute.expires)
            .bind(mute.unmuted)
            .bind(mute.pardoned)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn invalidate_previous_user_mutes(
        &self,
        guild_id: i64,
        user_id: i64,
    ) -> Result<(), Error> {
        sqlx::query(include_str!(
            "sql/mutes/invalidate_previous_mutes_of_user.sql"
        ))
        .bind(guild_id)
        .bind(user_id)
        .execute(&*self.0)
        .await?;

        Ok(())
    }

    pub async fn invalidate_mute(&self, id: i32) -> Result<(), Error> {
        sqlx::query(include_str!("sql/mutes/invalidate_entity.sql"))
            .bind(id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
