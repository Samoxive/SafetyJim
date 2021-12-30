mod invite_link;
mod word_filter;

use crate::database::settings::Setting;
use crate::discord::message_processors::invite_link::InviteLinkProcessor;
use crate::discord::message_processors::word_filter::WordFilterProcessor;
use crate::Config;
use async_trait::async_trait;
use serenity::client::Context;
use serenity::model::channel::Message;
use serenity::model::guild::PartialMember;
use serenity::model::id::GuildId;
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
        message: &Message,
        guild_id: GuildId,
        member: &PartialMember,
        permissions: Permissions,
        setting: &Setting,
        config: &Config,
        services: &TypeMap,
    ) -> anyhow::Result<bool>;
}
