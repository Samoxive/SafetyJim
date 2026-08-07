use std::num::NonZeroU64;

use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;

use crate::database::kicks::Kick;
use crate::server::model::user::UserModel;
use crate::service::Services;

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
    pub async fn from_kick(services: &Services, kick: &Kick) -> KickModel {
        let user = if let Some(id) = NonZeroU64::new(kick.user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        let moderator_user = if let Some(id) = NonZeroU64::new(kick.moderator_user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        KickModel {
            id: kick.id,
            user,
            moderator_user,
            action_time: kick.kick_time,
            reason: kick.reason.clone(),
            pardoned: kick.pardoned,
        }
    }
}
