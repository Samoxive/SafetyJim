use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

use crate::database::mutes::Mute;
use crate::server::model::user::UserModel;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MuteModel {
    pub id: i32,
    pub user: UserModel,
    pub moderator_user: UserModel,
    pub action_time: i64,
    pub expiration_time: i64,
    pub unmuted: bool,
    pub reason: String,
    pub pardoned: bool,
}

impl MuteModel {
    pub async fn from_mute(services: &TypeMap, mute: &Mute) -> MuteModel {
        MuteModel {
            id: mute.id,
            user: UserModel::from_id(services, UserId(mute.user_id as u64)).await,
            moderator_user: UserModel::from_id(services, UserId(mute.moderator_user_id as u64))
                .await,
            action_time: mute.mute_time,
            expiration_time: mute.expire_time,
            unmuted: mute.unmuted,
            reason: mute.reason.clone(),
            pardoned: mute.pardoned,
        }
    }
}
