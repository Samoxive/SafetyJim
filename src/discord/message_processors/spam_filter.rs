use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::time::Duration;

use anyhow::bail;
use async_trait::async_trait;
use moka::future::{Cache, CacheBuilder};
use serenity::client::Context;
use serenity::model::id::{ChannelId, GuildId, MessageId, UserId};
use serenity::model::user::User;
use serenity::model::Permissions;
use tokio::sync::Mutex;
use typemap_rev::TypeMap;

use crate::constants::{JIM_ID, JIM_ID_AND_TAG};
use crate::database::settings::Setting;
use crate::discord::message_processors::MessageProcessor;
use crate::discord::util::is_staff;
use crate::service::guild::GuildService;
use crate::service::hardban::HardbanService;

const REASON: &str = "Spamming messages with same content";
const REPETITION_THRESHOLD: u8 = 4;

#[derive(Clone)]
pub struct MessageRepetitionRecord {
    hash: u64,
    count: u8,
}

pub struct SpamFilterProcessor {
    message_hash_cache: Mutex<Cache<(GuildId, UserId), MessageRepetitionRecord>>,
}

impl SpamFilterProcessor {
    pub fn new() -> SpamFilterProcessor {
        SpamFilterProcessor {
            // to change the count of a given user, we must reinsert the record, which refreshes
            // the TTL timestamp. so instead we set TTI and TTL to the same value, bot spammers
            // are likely to send messages as fast as Discord API allows them anyways.
            message_hash_cache: Mutex::new(
                CacheBuilder::new(16000)
                    .time_to_idle(Duration::from_secs(10))
                    .time_to_live(Duration::from_secs(10))
                    .build(),
            ),
        }
    }
}

#[async_trait]
impl MessageProcessor for SpamFilterProcessor {
    async fn handle_message(
        &self,
        context: &Context,
        message_content: &str,
        guild_id: GuildId,
        channel_id: ChannelId,
        _message_id: MessageId,
        author: &User,
        permissions: Permissions,
        setting: &Setting,
        services: &TypeMap,
    ) -> anyhow::Result<bool> {
        if !setting.spam_filter {
            return Ok(false);
        }

        if is_staff(permissions) {
            return Ok(false);
        }

        // do the check
        // if user is not in cache, insert hash and 0
        // if user is in cache, check if hash is same
        // if hash is same, check if repetition is >= 5, if it is, hardban the user and invalidate cache
        // if it isn't, increment repetition and insert again
        // if hash is different, insert new hash with one repetition
        let mut hasher = DefaultHasher::new();
        message_content.hash(&mut hasher);
        let message_hash = hasher.finish();

        // critical section
        {
            let cache = self.message_hash_cache.lock().await;
            let cache_key = (guild_id, author.id);
            if let Some(mut record) = cache.get(&cache_key) {
                // we recorded user earlier, see if hash is same
                if record.hash == message_hash {
                    if record.count >= REPETITION_THRESHOLD {
                        // uh oh.
                    } else {
                        // user still has time, increment and insert
                        record.count += 1;
                        cache.invalidate(&cache_key).await;
                        cache.insert(cache_key, record).await;

                        return Ok(false);
                    }
                } else {
                    // different message, start from scratch
                    record.count = 1;
                    record.hash = message_hash;
                    cache.invalidate(&cache_key).await;
                    cache.insert(cache_key, record).await;

                    return Ok(false);
                }
            } else {
                let record = MessageRepetitionRecord {
                    hash: message_hash,
                    count: 1,
                };

                cache.insert(cache_key, record).await;
                return Ok(false);
            }
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

        let hardban_service = if let Some(service) = services.get::<HardbanService>() {
            service
        } else {
            bail!("couldn't get hardban service!");
        };

        let _ = hardban_service
            .issue_hardban(
                &context.http,
                guild_id,
                &guild.name,
                setting,
                Some(channel_id),
                JIM_ID,
                JIM_ID_AND_TAG,
                author,
                REASON.into(),
            )
            .await;

        Ok(true)
    }
}
