use std::sync::Arc;

use sqlx::{Error, PgPool};

use crate::util::now;

#[derive(sqlx::FromRow, Debug)]
pub struct Join {
    pub id: i32,
    pub user_id: i64,
    pub guild_id: i64,
    pub join_time: i64,
    pub allow_time: i64,
    pub allowed: bool,
}

pub struct JoinsRepository(pub Arc<PgPool>);

impl JoinsRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/joins/create_table.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_expired_joins(&self) -> Result<Vec<Join>, Error> {
        sqlx::query_as::<_, Join>(include_str!("sql/joins/select_expired_joins.sql"))
            .bind(now() as i64)
            .fetch_all(&*self.0)
            .await
    }

    pub async fn insert_join(&self, join: Join) -> Result<Join, Error> {
        sqlx::query_as::<_, Join>(include_str!("sql/joins/insert_entity.sql"))
            .bind(join.user_id)
            .bind(join.guild_id)
            .bind(join.join_time)
            .bind(join.allow_time)
            .bind(join.allowed)
            .fetch_one(&*self.0)
            .await
    }

    pub async fn _update_join(&self, join: Join) -> Result<(), Error> {
        sqlx::query(include_str!("sql/joins/update_entity.sql"))
            .bind(join.id)
            .bind(join.user_id)
            .bind(join.guild_id)
            .bind(join.join_time)
            .bind(join.allow_time)
            .bind(join.allowed)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn delete_guild_user_joins(&self, guild_id: i64, user_id: i64) -> Result<(), Error> {
        sqlx::query(include_str!("sql/joins/delete_guild_user_joins.sql"))
            .bind(guild_id)
            .bind(user_id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn invalidate_join(&self, id: i32) -> Result<(), Error> {
        sqlx::query(include_str!("sql/joins/invalidate_entity.sql"))
            .bind(id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
