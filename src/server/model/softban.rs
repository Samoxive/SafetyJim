use std::num::NonZeroU64;
use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

use crate::database::softbans::Softban;
use crate::server::model::user::UserModel;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SoftbanModel {
    pub id: i32,
    pub user: UserModel,
    pub moderator_user: UserModel,
    pub action_time: i64,
    pub reason: String,
    pub pardoned: bool,
}

impl SoftbanModel {
    pub async fn from_softban(services: &TypeMap, softban: &Softban) -> SoftbanModel {
        let user = if let Some(id) = NonZeroU64::new(softban.user_id as u64) {
            let user_id = UserId(id);
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        let moderator_user = if let Some(id) = NonZeroU64::new(softban.moderator_user_id as u64) {
            let user_id = UserId(id);
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        SoftbanModel {
            id: softban.id,
            user,
            moderator_user,
            action_time: softban.softban_time,
            reason: softban.reason.clone(),
            pardoned: softban.pardoned,
        }
    }
}
