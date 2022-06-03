use serde::{Deserialize, Serialize};
use serenity::model::id::ChannelId;

use crate::service::guild::CachedChannel;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ChannelModel {
    pub id: String,
    pub name: String,
}

impl ChannelModel {
    pub fn from_guild_channel(id: ChannelId, channel: &CachedChannel) -> ChannelModel {
        ChannelModel {
            id: id.to_string(),
            name: channel.name.clone(),
        }
    }
}
