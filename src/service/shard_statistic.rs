use std::collections::HashMap;

use serenity::client::bridge::gateway::{ShardId, ShardManager};
use tokio::sync::Mutex;
use typemap_rev::TypeMapKey;

impl TypeMapKey for ShardStatisticService {
    type Value = ShardStatisticService;
}

pub struct ShardStatisticService {
    shard_latencies: Mutex<HashMap<u64, u64>>,
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

    pub async fn update_latencies(&self, shard_manager: &ShardManager) {
        let mut shard_latencies = self.shard_latencies.lock().await;

        *shard_latencies = HashMap::new();

        for (shard_id, shard) in shard_manager.runners.lock().await.iter() {
            if let Some(shard_latency) = shard.latency {
                shard_latencies.insert(shard_id.0, shard_latency.as_millis() as u64);
            }
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
