use std::ops::Not;
use std::sync::Arc;

use sqlx::types::Uuid;
use sqlx::{Error, PgPool};

#[derive(sqlx::FromRow)]
pub struct InvalidUUID {
    pub user_id: Uuid,
}

pub struct InvalidUUIDsRepository(pub Arc<PgPool>);

impl InvalidUUIDsRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/invalid_uuids/create_table.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn is_uuid_invalid(&self, uuid: Uuid) -> Result<bool, Error> {
        Ok(
            sqlx::query(include_str!("sql/invalid_uuids/select_invalid_uuid.sql"))
                .bind(uuid)
                .fetch_all(&*self.0)
                .await?
                .is_empty()
                .not(),
        )
    }
}
