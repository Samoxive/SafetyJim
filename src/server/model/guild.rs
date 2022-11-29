use serde::{Deserialize, Serialize};
use serenity::model::id::GuildId;

use crate::service::guild::CachedGuild;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GuildModel {
    pub id: String,
    pub name: String,
    pub icon_url: String,
}

impl GuildModel {
    pub fn from_cached_guild(guild_id: GuildId, guild: &CachedGuild) -> GuildModel {
        GuildModel {
            id: guild_id.to_string(),
            name: guild.name.clone(),
            icon_url: guild.icon_url.clone().unwrap_or_default(),
        }
    }
}
