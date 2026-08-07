use std::num::NonZeroU64;

use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;

use crate::database::mutes::Mute;
use crate::server::model::user::UserModel;
use crate::service::Services;

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
    pub async fn from_mute(services: &Services, mute: &Mute) -> MuteModel {
        let user = if let Some(id) = NonZeroU64::new(mute.user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        let moderator_user = if let Some(id) = NonZeroU64::new(mute.moderator_user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        MuteModel {
            id: mute.id,
            user,
            moderator_user,
            action_time: mute.mute_time,
            expiration_time: mute.expire_time,
            unmuted: mute.unmuted,
            reason: mute.reason.clone(),
            pardoned: mute.pardoned,
        }
    }
}
