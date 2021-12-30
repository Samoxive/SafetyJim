use crate::database::bans::Ban;
use crate::server::model::user::UserModel;
use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

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
    pub async fn from_ban(services: &TypeMap, ban: &Ban) -> BanModel {
        BanModel {
            id: ban.id,
            user: UserModel::from_id(services, UserId(ban.user_id as u64)).await,
            moderator_user: UserModel::from_id(services, UserId(ban.moderator_user_id as u64))
                .await,
            action_time: ban.ban_time,
            expiration_time: ban.expire_time,
            unbanned: ban.unbanned,
            reason: ban.reason.clone(),
        }
    }
}
