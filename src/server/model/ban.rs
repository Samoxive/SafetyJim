use std::num::NonZeroU64;

use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;

use crate::database::bans::Ban;
use crate::server::model::user::UserModel;
use crate::service::Services;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BanModel {
    pub id: i32,
    pub user: UserModel,
    pub moderator_user: UserModel,
    pub action_time: i64,
    pub expiration_time: i64,
    pub unbanned: bool,
    pub reason: String,
}

impl BanModel {
    pub async fn from_ban(services: &Services, ban: &Ban) -> BanModel {
        let user = if let Some(id) = NonZeroU64::new(ban.user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            // should never happen since user ids come from Discord
            Default::default()
        };

        let moderator_user = if let Some(id) = NonZeroU64::new(ban.moderator_user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        BanModel {
            id: ban.id,
            user,
            moderator_user,
            action_time: ban.ban_time,
            expiration_time: ban.expire_time,
            unbanned: ban.unbanned,
            reason: ban.reason.clone(),
        }
    }
}
