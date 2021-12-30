use crate::database::kicks::Kick;
use crate::server::model::user::UserModel;
use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct KickModel {
    pub id: i32,
    pub user: UserModel,
    pub moderator_user: UserModel,
    pub action_time: i64,
    pub reason: String,
    pub pardoned: bool,
}

impl KickModel {
    pub async fn from_kick(services: &TypeMap, kick: &Kick) -> KickModel {
        KickModel {
            id: kick.id,
            user: UserModel::from_id(services, UserId(kick.user_id as u64)).await,
            moderator_user: UserModel::from_id(services, UserId(kick.moderator_user_id as u64))
                .await,
            action_time: kick.kick_time,
            reason: kick.reason.clone(),
            pardoned: kick.pardoned,
        }
    }
}
