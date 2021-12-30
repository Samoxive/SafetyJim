use crate::server::model::guild::GuildModel;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SelfUserModel {
    pub id: String,
    pub name: String,
    pub avatar_url: String,
    pub guilds: Vec<GuildModel>,
}
