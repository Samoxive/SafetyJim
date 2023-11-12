use std::num::NonZeroU64;

use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;

use crate::server::model::user::UserModel;
use crate::database::warns::Warn;
use crate::service::Services;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WarnModel {
    pub id: i32,
    pub user: UserModel,
    pub moderator_user: UserModel,
    pub action_time: i64,
    pub reason: String,
    pub pardoned: bool,
}

impl WarnModel {
    pub async fn from_warn(services: &Services, warn: &Warn) -> WarnModel {
        let user = if let Some(id) = NonZeroU64::new(warn.user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        let moderator_user = if let Some(id) = NonZeroU64::new(warn.moderator_user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        WarnModel {
            id: warn.id,
            user,
            moderator_user,
            action_time: warn.warn_time,
            reason: warn.reason.clone(),
            pardoned: warn.pardoned,
        }
    }
}
