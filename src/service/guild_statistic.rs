use serenity::model::guild::{Guild, UnavailableGuild};
use serenity::model::id::GuildId;
use std::collections::HashMap;
use tokio::sync::Mutex;
use typemap_rev::TypeMapKey;

impl TypeMapKey for GuildStatisticService {
    type Value = GuildStatisticService;
}

pub struct GuildStatisticService {
    member_counts: Mutex<HashMap<GuildId, u64>>,
}

pub struct GuildStatistics {
    pub guild_count: u64,
    pub member_count: u64,
}

impl GuildStatisticService {
    pub fn new() -> Self {
        GuildStatisticService {
            member_counts: Mutex::new(HashMap::new()),
        }
    }

    pub async fn add_guild(&self, guild: &Guild) {
        let member_count = guild.member_count;
        let guild_id = guild.id;

        self.member_counts
            .lock()
            .await
            .insert(guild_id, member_count);
    }

    pub async fn remove_guild(&self, guild: &UnavailableGuild) {
        let guild_id = guild.id;
        self.member_counts.lock().await.remove(&guild_id);
    }

    pub async fn increment_guild_member_count(&self, guild_id: GuildId) {
        *(self.member_counts.lock().await.entry(guild_id).or_insert(0)) += 1;
    }

    pub async fn decrement_guild_member_count(&self, guild_id: GuildId) {
        *(self.member_counts.lock().await.entry(guild_id).or_insert(1)) -= 1;
    }

    pub async fn get_guild_statistics(&self) -> GuildStatistics {
        let member_counts_map = self.member_counts.lock().await;

        GuildStatistics {
            guild_count: member_counts_map.keys().len() as u64,
            member_count: member_counts_map.values().sum::<u64>(),
        }
    }

    pub async fn filter_known_guilds(&self, guild_ids: &[GuildId]) -> Vec<GuildId> {
        let member_count_lock = self.member_counts.lock().await;

        guild_ids
            .iter()
            .filter(|id| member_count_lock.contains_key(id))
            .cloned()
            .collect()
    }
}
