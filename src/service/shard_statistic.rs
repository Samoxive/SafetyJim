use crate::discord::discord_bot::DiscordBot;
use serenity::all::ShardId;
use std::collections::HashMap;
use std::sync::Arc;
use tokio::sync::Mutex;
use typemap_rev::TypeMapKey;

impl TypeMapKey for ShardStatisticService {
    type Value = ShardStatisticService;
}

pub struct ShardStatisticService {
    shard_latencies: Mutex<HashMap<u16, u64>>,
}

pub struct ShardLatencyInfo {
    pub current_shard_latency: u64,
    pub average_shard_latency: u64,
    pub total_shard_count: u32,
}

impl ShardStatisticService {
    pub fn new() -> Self {
        ShardStatisticService {
            shard_latencies: Mutex::new(HashMap::new()),
        }
    }

    pub async fn get_shard_latency_info(&self, shard_id: ShardId) -> ShardLatencyInfo {
        let shard_latencies = self.shard_latencies.lock().await;

        if shard_latencies.len() == 0 {
            return ShardLatencyInfo {
                current_shard_latency: 0,
                average_shard_latency: 0,
                total_shard_count: 0,
            };
        }

        let total_shard_count = shard_latencies.len() as u32;
        let current_shard_latency = shard_latencies.get(&shard_id.0).copied().unwrap_or(0);
        let average_shard_latency =
            shard_latencies.values().sum::<u64>() / total_shard_count as u64;

        ShardLatencyInfo {
            current_shard_latency,
            average_shard_latency,
            total_shard_count,
        }
    }
}
