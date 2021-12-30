use std::sync::Arc;

use sqlx::{Error, PgPool, Row};

#[derive(sqlx::FromRow)]
pub struct Hardban {
    pub id: i32,
    pub user_id: i64,
    pub moderator_user_id: i64,
    pub guild_id: i64,
    pub hardban_time: i64,
    pub reason: String,
}

pub struct HardbansRepository(pub Arc<PgPool>);

impl HardbansRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/hardbans/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!("sql/hardbans/create_hardban_time_index.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_hardban(&self, id: i32) -> Result<Option<Hardban>, Error> {
        Ok(
            sqlx::query_as::<_, Hardban>(include_str!("sql/hardbans/select_hardban_with_id.sql"))
                .bind(id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn fetch_guild_hardbans(
        &self,
        guild_id: i64,
        page: u32,
    ) -> Result<Vec<Hardban>, Error> {
        Ok(sqlx::query_as::<_, Hardban>(include_str!(
            "sql/hardbans/select_guild_hardbans_paginated.sql"
        ))
        .bind(guild_id)
        .bind((page - 1) * 10)
        .fetch_all(&*self.0)
        .await?)
    }

    pub async fn fetch_guild_hardban_count(&self, guild_id: i64) -> Result<i64, Error> {
        Ok(
            sqlx::query(include_str!("sql/hardbans/count_guild_hardbans.sql"))
                .bind(guild_id)
                .fetch_one(&*self.0)
                .await?
                .get(0),
        )
    }

    pub async fn insert_hardban(&self, hardban: Hardban) -> Result<Hardban, Error> {
        Ok(
            sqlx::query_as::<_, Hardban>(include_str!("sql/hardbans/insert_entity.sql"))
                .bind(hardban.user_id)
                .bind(hardban.moderator_user_id)
                .bind(hardban.guild_id)
                .bind(hardban.hardban_time)
                .bind(hardban.reason)
                .fetch_one(&*self.0)
                .await?,
        )
    }

    pub async fn update_hardban(&self, hardban: Hardban) -> Result<(), Error> {
        sqlx::query(include_str!("sql/hardbans/update_entity.sql"))
            .bind(hardban.id)
            .bind(hardban.user_id)
            .bind(hardban.moderator_user_id)
            .bind(hardban.guild_id)
            .bind(hardban.hardban_time)
            .bind(hardban.reason)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
