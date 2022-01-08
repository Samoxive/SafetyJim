mod invite_link;
mod word_filter;

use crate::database::settings::Setting;
use crate::discord::message_processors::invite_link::InviteLinkProcessor;
use crate::discord::message_processors::word_filter::WordFilterProcessor;
use async_trait::async_trait;
use serenity::client::Context;
use serenity::model::id::{ChannelId, GuildId, MessageId};
use serenity::model::user::User;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

pub struct MessageProcessors(pub Vec<Box<dyn MessageProcessor + Send + Sync>>);

pub fn get_all_processors() -> MessageProcessors {
    MessageProcessors(vec![
        Box::new(InviteLinkProcessor),
        Box::new(WordFilterProcessor),
    ])
}

#[async_trait]
pub trait MessageProcessor {
    async fn handle_message(
        &self,
        context: &Context,
        message_content: &str,
        guild_id: GuildId,
        channel_id: ChannelId,
        message_id: MessageId,
        author: &User,
        permissions: Permissions,
        setting: &Setting,
        services: &TypeMap,
    ) -> anyhow::Result<bool>;
}
