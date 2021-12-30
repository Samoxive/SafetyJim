use std::sync::Arc;

use sqlx::{Error, PgPool};

#[derive(sqlx::FromRow)]
pub struct IAMRole {
    pub id: i32,
    pub guild_id: i64,
    pub role_id: i64,
}

pub struct IAMRolesRepository(pub Arc<PgPool>);

impl IAMRolesRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/iam_roles/create_table.sql"))
            .execute(&*self.0)
            .await?;
        sqlx::query(include_str!(
            "sql/iam_roles/create_iam_roles_index_guild_id_role_id.sql"
        ))
        .execute(&*self.0)
        .await?;
        Ok(())
    }

    pub async fn fetch_guild_iam_role(
        &self,
        guild_id: i64,
        role_id: i64,
    ) -> Result<Option<IAMRole>, Error> {
        Ok(
            sqlx::query_as::<_, IAMRole>(include_str!("sql/iam_roles/select_guild_iam_role.sql"))
                .bind(guild_id)
                .bind(role_id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn _fetch_guild_iam_roles(&self, guild_id: i64) -> Result<Vec<IAMRole>, Error> {
        Ok(
            sqlx::query_as::<_, IAMRole>(include_str!("sql/iam_roles/select_guild_iam_roles.sql"))
                .bind(guild_id)
                .fetch_all(&*self.0)
                .await?,
        )
    }

    pub async fn insert_iam_role(&self, iam_role: IAMRole) -> Result<IAMRole, Error> {
        Ok(
            sqlx::query_as::<_, IAMRole>(include_str!("sql/iam_roles/insert_entity.sql"))
                .bind(iam_role.guild_id)
                .bind(iam_role.role_id)
                .fetch_one(&*self.0)
                .await?,
        )
    }

    pub async fn delete_iam_role(&self, iam_role_id: i32) -> Result<(), Error> {
        sqlx::query(include_str!("sql/iam_roles/delete_iam_role.sql"))
            .bind(iam_role_id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
