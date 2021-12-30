use std::sync::Arc;

use sqlx::{Error, PgPool, Row};

#[derive(sqlx::FromRow)]
pub struct Warn {
    pub id: i32,
    pub user_id: i64,
    pub moderator_user_id: i64,
    pub guild_id: i64,
    pub warn_time: i64,
    pub reason: String,
    pub pardoned: bool,
}

pub struct WarnsRepository(pub Arc<PgPool>);

impl WarnsRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/warns/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!("sql/warns/create_warn_time_index.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_warn(&self, id: i32) -> Result<Option<Warn>, Error> {
        Ok(
            sqlx::query_as::<_, Warn>(include_str!("sql/warns/select_warn_with_id.sql"))
                .bind(id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_warns(&self, guild_id: i64, page: u32) -> Result<Vec<Warn>, Error> {
        Ok(
            sqlx::query_as::<_, Warn>(include_str!("sql/warns/select_guild_warns_paginated.sql"))
                .bind(guild_id)
                .bind((page - 1) * 10)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_warn_count(&self, guild_id: i64) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!("sql/warns/count_guild_warns.sql"))
            .bind(guild_id)
            .fetch_one(&*self.0)
            .await?
            .get(0))
    }

    pub async fn fetch_actionable_warn_count(
        &self,
        guild_id: i64,
        user_id: i64,
    ) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!(
            "sql/warns/count_actionable_guild_user_warns.sql"
        ))
        .bind(guild_id)
        .bind(user_id)
        .fetch_one(&*self.0)
        .await?
        .get(0))
    }

    pub async fn insert_warn(&self, warn: Warn) -> Result<Warn, Error> {
        Ok(
            sqlx::query_as::<_, Warn>(include_str!("sql/warns/insert_entity.sql"))
                .bind(warn.user_id)
                .bind(warn.moderator_user_id)
                .bind(warn.guild_id)
                .bind(warn.warn_time)
                .bind(warn.reason)
                .bind(warn.pardoned)
                .fetch_one(&*self.0)
                .await?,
        )
    }

    pub async fn update_warn(&self, warn: Warn) -> Result<(), Error> {
        sqlx::query(include_str!("sql/warns/update_entity.sql"))
            .bind(warn.id)
            .bind(warn.user_id)
            .bind(warn.moderator_user_id)
            .bind(warn.guild_id)
            .bind(warn.warn_time)
            .bind(warn.reason)
            .bind(warn.pardoned)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
