use crate::config::Config;
use crate::constants::{DEFAULT_BLOCKED_WORDS, JIM_ID, JIM_ID_AND_TAG};
use crate::database::settings::{
    get_action_duration_for_auto_mod_action, Setting, WORD_FILTER_LEVEL_HIGH, WORD_FILTER_LEVEL_LOW,
};
use crate::discord::message_processors::MessageProcessor;
use crate::discord::util::{execute_mod_action, is_staff, SerenityErrorExt};
use crate::service::guild::GuildService;
use anyhow::bail;
use async_trait::async_trait;
use serenity::client::Context;
use serenity::model::channel::Message;
use serenity::model::guild::PartialMember;
use serenity::model::id::GuildId;
use serenity::model::Permissions;
use serenity::prelude::TypeMap;
use smol_str::SmolStr;

use anyhow::anyhow;
use tracing::{error, warn};

const REASON: &str = "Using blocklisted word(s).";

pub struct WordFilterProcessor;

// unoptimized, maybe use Trie/Set for lookup, strings are small enough to work fast with O(n^2)
fn filter_message_simple(content: &str, blocked_words: &[SmolStr]) -> bool {
    for word in content.split_whitespace() {
        for blocked_word in blocked_words {
            if word == blocked_word {
                return true;
            }
        }
    }

    false
}

// unoptimized, maybe use Aho-Corasick pattern matching instead of O(n^2)
fn filter_message_deep(content: &str, blocked_words: &[SmolStr]) -> bool {
    for word in content.split_whitespace() {
        for blocked_word in blocked_words {
            if word.contains(blocked_word.as_str()) {
                return true;
            }
        }
    }

    false
}

fn filter_message(setting: &Setting, content: &str, blocked_words: &[SmolStr]) -> bool {
    if setting.word_filter_level == WORD_FILTER_LEVEL_LOW {
        filter_message_simple(content, blocked_words)
    } else if setting.word_filter_level == WORD_FILTER_LEVEL_HIGH {
        filter_message_deep(content, blocked_words)
    } else {
        warn!(guild_id = setting.guild_id, "invalid word filter level");
        filter_message_simple(content, blocked_words)
    }
}

#[async_trait]
impl MessageProcessor for WordFilterProcessor {
    async fn handle_message(
        &self,
        context: &Context,
        message: &Message,
        guild_id: GuildId,
        _member: &PartialMember,
        permissions: Permissions,
        setting: &Setting,
        _config: &Config,
        services: &TypeMap,
    ) -> anyhow::Result<bool> {
        if !setting.word_filter {
            return Ok(false);
        }

        if is_staff(permissions) {
            return Ok(false);
        }

        let result = if let Some(block_list) = &setting.word_filter_blocklist {
            let filter = block_list
                .split(',')
                .map(SmolStr::new)
                .collect::<Vec<SmolStr>>();

            filter_message(setting, &message.content, &filter)
        } else {
            let filter = DEFAULT_BLOCKED_WORDS
                .get()
                .ok_or_else(|| anyhow!("failed to get default blocked words!"))?;
            filter_message(setting, &message.content, filter)
        };

        if !result {
            return Ok(false);
        }

        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            bail!("couldn't get guild service!");
        };

        let guild = if let Ok(guild) = guild_service.get_guild(guild_id).await {
            guild
        } else {
            bail!("couldn't get guild name!");
        };

        let duration = get_action_duration_for_auto_mod_action(
            setting.word_filter_action,
            setting.word_filter_action_duration_type,
            setting.word_filter_action_duration,
        );

        match message.delete(&context.http).await {
            Ok(_) => {
                execute_mod_action(
                    setting.word_filter_action,
                    &*context.http,
                    guild_id,
                    &guild.name,
                    setting,
                    services,
                    Some(message.channel_id),
                    JIM_ID,
                    JIM_ID_AND_TAG,
                    &message.author,
                    REASON.into(),
                    duration,
                    0,
                )
                .await;
                Ok(true)
            }
            Err(err) => {
                match err.discord_error_code() {
                    Some(50013) => (),
                    _ => {
                        error!("failed to delete message for censorship {}", err);
                    }
                }
                Ok(false)
            },
        }
    }
}
