use serde::{Deserialize, Serialize};

use crate::server::model::guild::GuildModel;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SelfUserModel {
    pub id: String,
    pub name: String,
    pub avatar_url: String,
    pub guilds: Vec<GuildModel>,
}
