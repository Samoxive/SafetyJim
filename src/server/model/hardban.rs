use crate::database::hardbans::Hardban;
use crate::server::model::user::UserModel;
use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

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
    pub async fn from_hardban(services: &TypeMap, hardban: &Hardban) -> HardbanModel {
        HardbanModel {
            id: hardban.id,
            user: UserModel::from_id(services, UserId(hardban.user_id as u64)).await,
            moderator_user: UserModel::from_id(services, UserId(hardban.moderator_user_id as u64))
                .await,
            action_time: hardban.hardban_time,
            reason: hardban.reason.clone(),
        }
    }
}
