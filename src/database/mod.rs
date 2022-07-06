use sqlx::postgres::PgPoolOptions;
use sqlx::{Pool, Postgres};

use crate::Config;

pub mod bans;
pub mod hardbans;
pub mod iam_roles;
pub mod invalid_uuids;
pub mod joins;
pub mod kicks;
pub mod mutes;
pub mod reminders;
pub mod settings;
pub mod softbans;
pub mod tags;
pub mod user_secrets;
pub mod warns;

pub async fn setup_database_pool(config: &Config) -> Result<Pool<Postgres>, sqlx::Error> {
    PgPoolOptions::new()
        .max_connections(5)
        .connect(
            &config.database_url
        )
        .await
}
