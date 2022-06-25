use std::sync::Arc;
use std::time::Duration;

use moka::future::{Cache, CacheBuilder};
use serenity::model::id::GuildId;
use tracing::error;
use typemap_rev::TypeMapKey;

use crate::database::settings::{Setting, SettingsRepository};

impl TypeMapKey for SettingService {
    type Value = SettingService;
}

pub struct SettingService {
    pub repository: SettingsRepository,
    pub setting_cache: Cache<GuildId, Arc<Setting>>,
}

impl SettingService {
    pub fn new(repository: SettingsRepository) -> SettingService {
        SettingService {
            repository,
            setting_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(30))
                .time_to_live(Duration::from_secs(60))
                .build(),
        }
    }

    pub async fn get_setting(&self, guild_id: GuildId) -> Arc<Setting> {
        if let Some(cached) = self.setting_cache.get(&guild_id) {
            return cached;
        }

        if let Some(fetched) = self.fetch_setting(guild_id).await {
            return fetched;
        }

        let created = match self
            .repository
            .insert_setting(Setting::default(guild_id))
            .await
        {
            Ok(setting) => setting,
            Err(err) => {
                error!("failed to insert guild setting {:?}", err);
                match self.repository.fetch_setting(guild_id.0.get() as i64).await {
                    Ok(Some(setting)) => setting,
                    Ok(None) => Setting::default(guild_id),
                    Err(err) => {
                        error!("failed to fetch setting {:?}", err);
                        Setting::default(guild_id)
                    }
                }
            }
        };

        let created_boxed = Arc::new(created);
        self.setting_cache
            .insert(guild_id, created_boxed.clone())
            .await;
        created_boxed
    }

    pub async fn fetch_setting(&self, guild_id: GuildId) -> Option<Arc<Setting>> {
        self.repository
            .fetch_setting(guild_id.0.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to fetch setting {:?}", err);
                err
            })
            .ok()
            .flatten()
            .map(Arc::new)
    }

    pub async fn update_setting(&self, guild_id: GuildId, new_setting: Setting) {
        let _ = self
            .repository
            .update_setting(new_setting)
            .await
            .map_err(|err| {
                error!("failed to update setting {:?}", err);
                err
            });

        self.setting_cache.invalidate(&guild_id).await;
    }

    pub async fn reset_setting(&self, guild_id: GuildId) {
        let _ = self
            .repository
            .delete_setting(guild_id.0.get() as i64)
            .await
            .map_err(|err| {
                error!("failed to delete setting {:?}", err);
                err
            });

        self.setting_cache.invalidate(&guild_id).await;
    }
}
