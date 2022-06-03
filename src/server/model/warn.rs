use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

use crate::database::warns::Warn;
use crate::server::model::user::UserModel;

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
    pub async fn from_warn(services: &TypeMap, warn: &Warn) -> WarnModel {
        WarnModel {
            id: warn.id,
            user: UserModel::from_id(services, UserId(warn.user_id as u64)).await,
            moderator_user: UserModel::from_id(services, UserId(warn.moderator_user_id as u64))
                .await,
            action_time: warn.warn_time,
            reason: warn.reason.clone(),
            pardoned: warn.pardoned,
        }
    }
}
