use serde::{Deserialize, Serialize};
use serenity::model::id::RoleId;

use crate::service::guild::CachedRole;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoleModel {
    pub id: String,
    pub name: String,
}

impl RoleModel {
    pub fn from_role(id: RoleId, role: &CachedRole) -> RoleModel {
        RoleModel {
            id: id.to_string(),
            name: role.name.clone(),
        }
    }
}
