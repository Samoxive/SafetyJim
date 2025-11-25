use async_trait::async_trait;
use serenity::all::{Context, GenericChannelId};
use serenity::model::id::{GuildId, MessageId};
use serenity::model::user::User;
use serenity::model::Permissions;

use crate::database::settings::Setting;
use crate::discord::message_processors::invite_link::InviteLinkProcessor;
use crate::discord::message_processors::spam_filter::SpamFilterProcessor;
use crate::discord::message_processors::word_filter::WordFilterProcessor;
use crate::service::Services;

mod invite_link;
mod spam_filter;
mod word_filter;

pub struct MessageProcessors(pub Vec<Box<dyn MessageProcessor + Send + Sync>>);

pub fn get_all_processors() -> MessageProcessors {
    MessageProcessors(vec![
        // needs to be run first to detect spam even if it contains blocklisted words
        // or a Discord server invite link
        Box::new(SpamFilterProcessor::new()),
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
        channel_id: GenericChannelId,
        message_id: MessageId,
        author: &User,
        permissions: Permissions,
        setting: &Setting,
        services: &Services,
    ) -> anyhow::Result<bool>;
}
