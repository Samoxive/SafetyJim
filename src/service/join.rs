use serenity::model::id::{GuildId, UserId};
use tracing::error;
use typemap_rev::TypeMapKey;

use crate::database::joins::{Join, JoinsRepository};
use crate::util::now;

impl TypeMapKey for JoinService {
    type Value = JoinService;
}

pub struct JoinService {
    pub repository: JoinsRepository,
}

impl JoinService {
    pub async fn issue_join(&self, guild_id: GuildId, user_id: UserId, wait_time: i32) {
        let now = now();
        let allow_time = now + (wait_time as u64 * 60);

        let _ = self
            .repository
            .insert_join(Join {
                id: 0,
                user_id: user_id.get() as i64,
                guild_id: guild_id.get() as i64,
                join_time: now as i64,
                allow_time: allow_time as i64,
                allowed: false,
            })
            .await
            .map_err(|err| {
                error!("failed to insert join {:?}", err);
                err
            });
    }
    pub async fn get_expired_joins(&self) -> Vec<Join> {
        self.repository
            .fetch_expired_joins()
            .await
            .map_err(|err| {
                error!("failed to fetch expired joins {:?}", err);
                err
            })
            .ok()
            .unwrap_or_default()
    }

    pub async fn invalidate_join(&self, id: i32) {
        let _ = self.repository.invalidate_join(id).await.map_err(|err| {
            error!("failed to invalidate join {:?}", err);
            err
        });
    }

    pub async fn delete_user_joins(&self, guild_id: GuildId, user_id: UserId) {
        let _ = self
            .repository
            .delete_guild_user_joins(guild_id.get() as i64, user_id.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to delete user joins {:?}", err);
                err
            });
    }
}
