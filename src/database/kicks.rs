use std::sync::Arc;

use sqlx::{Error, PgPool, Row};

#[derive(sqlx::FromRow)]
pub struct Kick {
    pub id: i32,
    pub user_id: i64,
    pub moderator_user_id: i64,
    pub guild_id: i64,
    pub kick_time: i64,
    pub reason: String,
    pub pardoned: bool,
}

pub struct KicksRepository(pub Arc<PgPool>);

impl KicksRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/kicks/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!("sql/kicks/create_kick_time_index.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_kick(&self, id: i32) -> Result<Option<Kick>, Error> {
        Ok(
            sqlx::query_as::<_, Kick>(include_str!("sql/kicks/select_kick_with_id.sql"))
                .bind(id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_kicks(&self, guild_id: i64, page: u32) -> Result<Vec<Kick>, Error> {
        Ok(
            sqlx::query_as::<_, Kick>(include_str!("sql/kicks/select_guild_kicks_paginated.sql"))
                .bind(guild_id)
                .bind((page - 1) * 10)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_kick_count(&self, guild_id: i64) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!("sql/kicks/count_guild_kicks.sql"))
            .bind(guild_id)
            .fetch_one(&*self.0)
            .await?
            .get(0))
    }

    pub async fn fetch_actionable_kick_count(
        &self,
        guild_id: i64,
        user_id: i64,
    ) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!(
            "sql/kicks/count_actionable_guild_user_kicks.sql"
        ))
        .bind(guild_id)
        .bind(user_id)
        .fetch_one(&*self.0)
        .await?
        .get(0))
    }

    pub async fn insert_kick(&self, kick: Kick) -> Result<Kick, Error> {
        Ok(
            sqlx::query_as::<_, Kick>(include_str!("sql/kicks/insert_entity.sql"))
                .bind(kick.user_id)
                .bind(kick.moderator_user_id)
                .bind(kick.guild_id)
                .bind(kick.kick_time)
                .bind(kick.reason)
                .bind(kick.pardoned)
                .fetch_one(&*self.0)
                .await?,
        )
    }

    pub async fn update_kick(&self, kick: Kick) -> Result<(), Error> {
        sqlx::query(include_str!("sql/kicks/update_entity.sql"))
            .bind(kick.id)
            .bind(kick.user_id)
            .bind(kick.moderator_user_id)
            .bind(kick.guild_id)
            .bind(kick.kick_time)
            .bind(kick.reason)
            .bind(kick.pardoned)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
