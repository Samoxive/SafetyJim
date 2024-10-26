use std::num::NonZeroU64;

use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;

use crate::database::hardbans::Hardban;
use crate::server::model::user::UserModel;
use crate::service::Services;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HardbanModel {
    pub id: i32,
    pub user: UserModel,
    pub moderator_user: UserModel,
    pub action_time: i64,
    pub reason: String,
}

impl HardbanModel {
    pub async fn from_hardban(services: &Services, hardban: &Hardban) -> HardbanModel {
        let user = if let Some(id) = NonZeroU64::new(hardban.user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        let moderator_user = if let Some(id) = NonZeroU64::new(hardban.moderator_user_id as u64) {
            let user_id = UserId::new(id.get());
            UserModel::from_id(services, user_id).await
        } else {
            Default::default()
        };

        HardbanModel {
            id: hardban.id,
            user,
            moderator_user,
            action_time: hardban.hardban_time,
            reason: hardban.reason.clone(),
        }
    }
}
