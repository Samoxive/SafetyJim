use std::sync::Arc;

use sqlx::{Error, PgPool};

use crate::util::now;

#[derive(sqlx::FromRow)]
pub struct UserSecret {
    pub user_id: i64,
    pub access_token: String,
}

pub struct UserSecretsRepository(pub Arc<PgPool>);

impl UserSecretsRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/user_secrets/create_table.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_user_secret(&self, user_id: i64) -> Result<Option<UserSecret>, Error> {
        sqlx::query_as::<_, UserSecret>(include_str!("sql/user_secrets/select_user_secret.sql"))
            .bind(user_id)
            .fetch_optional(&*self.0)
            .await
    }

    pub async fn upsert_user_secret(&self, user_secret: UserSecret) -> Result<(), Error> {
        sqlx::query(include_str!("sql/user_secrets/upsert_entity.sql"))
            .bind(user_secret.user_id)
            .bind(user_secret.access_token)
            .bind(now() as i64)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
