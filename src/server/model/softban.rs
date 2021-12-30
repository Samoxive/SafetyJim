use crate::database::softbans::Softban;
use crate::server::model::user::UserModel;
use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

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
        SoftbanModel {
            id: softban.id,
            user: UserModel::from_id(services, UserId(softban.user_id as u64)).await,
            moderator_user: UserModel::from_id(services, UserId(softban.moderator_user_id as u64))
                .await,
            action_time: softban.softban_time,
            reason: softban.reason.clone(),
            pardoned: softban.pardoned,
        }
    }
}
