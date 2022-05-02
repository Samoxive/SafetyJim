use std::sync::Arc;

use sqlx::{Error, PgPool, Row};

#[derive(sqlx::FromRow)]
pub struct Softban {
    pub id: i32,
    pub user_id: i64,
    pub moderator_user_id: i64,
    pub guild_id: i64,
    pub softban_time: i64,
    pub reason: String,
    pub pardoned: bool,
}

pub struct SoftbansRepository(pub Arc<PgPool>);

impl SoftbansRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/softbans/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!("sql/softbans/create_softban_time_index.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_softban(&self, id: i32) -> Result<Option<Softban>, Error> {
        sqlx::query_as::<_, Softban>(include_str!("sql/softbans/select_softban_with_id.sql"))
            .bind(id)
            .fetch_optional(&*self.0)
            .await
    }

    pub async fn fetch_guild_softbans(
        &self,
        guild_id: i64,
        page: u32,
    ) -> Result<Vec<Softban>, Error> {
        sqlx::query_as::<_, Softban>(include_str!(
            "sql/softbans/select_guild_softbans_paginated.sql"
        ))
        .bind(guild_id)
        .bind((page - 1) * 10)
        .fetch_all(&*self.0)
        .await
    }

    pub async fn fetch_guild_softban_count(&self, guild_id: i64) -> Result<i64, Error> {
        Ok(
            sqlx::query(include_str!("sql/softbans/count_guild_softbans.sql"))
                .bind(guild_id)
                .fetch_one(&*self.0)
                .await?
                .get(0),
        )
    }

    pub async fn fetch_actionable_softban_count(
        &self,
        guild_id: i64,
        user_id: i64,
    ) -> Result<i64, Error> {
        Ok(sqlx::query(include_str!(
            "sql/softbans/count_actionable_guild_user_softbans.sql"
        ))
        .bind(guild_id)
        .bind(user_id)
        .fetch_one(&*self.0)
        .await?
        .get(0))
    }

    pub async fn insert_softban(&self, softban: Softban) -> Result<Softban, Error> {
        sqlx::query_as::<_, Softban>(include_str!("sql/softbans/insert_entity.sql"))
            .bind(softban.user_id)
            .bind(softban.moderator_user_id)
            .bind(softban.guild_id)
            .bind(softban.softban_time)
            .bind(softban.reason)
            .bind(softban.pardoned)
            .fetch_one(&*self.0)
            .await
    }

    pub async fn update_softban(&self, softban: Softban) -> Result<(), Error> {
        sqlx::query(include_str!("sql/softbans/update_entity.sql"))
            .bind(softban.id)
            .bind(softban.user_id)
            .bind(softban.moderator_user_id)
            .bind(softban.guild_id)
            .bind(softban.softban_time)
            .bind(softban.reason)
            .bind(softban.pardoned)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
