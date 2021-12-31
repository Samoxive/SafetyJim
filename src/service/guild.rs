use crate::discord::util::get_permissions;
use moka::future::{Cache, CacheBuilder};
use serenity::http::Http;
use serenity::model::id::{ChannelId, GuildId, RoleId, UserId};
use serenity::model::Permissions;
use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;
use thiserror::Error;
use tokio::sync::RwLock;
use typemap_rev::TypeMapKey;

impl TypeMapKey for GuildService {
    type Value = GuildService;
}

pub struct CachedGuild {
    pub name: String,
    pub icon_url: Option<String>,
    pub owner_id: UserId,
}

pub struct CachedMember {
    pub roles: Vec<RoleId>,
}

pub struct CachedRole {
    pub name: String,
    pub permissions: Permissions,
}

pub struct CachedUser {
    pub tag: String,
    pub avatar_url: String,
}

pub struct CachedChannel {
    pub name: String,
}

pub struct GuildService {
    http: Arc<RwLock<Option<Arc<Http>>>>,
    guild_cache: Cache<GuildId, Arc<CachedGuild>>,
    role_cache: Cache<GuildId, Arc<HashMap<RoleId, CachedRole>>>,
    member_cache: Cache<(GuildId, UserId), Arc<CachedMember>>,
    user_cache: Cache<UserId, Arc<CachedUser>>,
    channel_cache: Cache<GuildId, Arc<HashMap<ChannelId, CachedChannel>>>,
}

pub enum GetPermissionsFailure {
    FetchFailed,
    Calculation,
}

#[derive(Error, Debug)]
pub enum GetGuildFailure {
    #[error("Fetch failed")]
    FetchFailed,
}

pub enum GetMemberFailure {
    FetchFailed,
}

#[derive(Error, Debug)]
pub enum GetUserFailure {
    #[error("Fetch failed")]
    FetchFailed,
}

pub enum GetRolesFailure {
    FetchFailed,
}

pub enum GetChannelsFailure {
    FetchFailed,
}

impl GuildService {
    pub fn new() -> GuildService {
        GuildService {
            http: Arc::new(RwLock::new(None)),
            guild_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(30))
                .time_to_live(Duration::from_secs(60))
                .build(),
            role_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(30))
                .time_to_live(Duration::from_secs(60))
                .build(),
            member_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(30))
                .time_to_live(Duration::from_secs(60))
                .build(),
            user_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(30))
                .time_to_live(Duration::from_secs(60))
                .build(),
            channel_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(30))
                .time_to_live(Duration::from_secs(60))
                .build(),
        }
    }

    pub async fn invalidate_cached_guild(&self, guild_id: GuildId) {
        self.guild_cache.invalidate(&guild_id).await;
    }

    pub async fn invalidate_cached_guild_roles(&self, guild_id: GuildId) {
        self.role_cache.invalidate(&guild_id).await;
    }

    pub async fn invalidate_cached_guild_member(&self, guild_id: GuildId, user_id: UserId) {
        self.member_cache.invalidate(&(guild_id, user_id)).await;
    }

    pub async fn invalidate_cached_user(&self, user_id: UserId) {
        self.user_cache.invalidate(&user_id).await;
    }

    pub async fn invalidate_cached_guild_channels(&self, guild_id: GuildId) {
        self.channel_cache.invalidate(&guild_id).await;
    }

    pub async fn http(&self) -> Arc<Http> {
        self.http
            .read()
            .await
            .as_ref()
            .expect("http client requested *before* it was inserted!")
            .clone()
    }

    pub async fn insert_http(&self, http: Arc<Http>) {
        self.http.write().await.replace(http);
    }

    pub async fn get_permissions(
        &self,
        member_roles: &[RoleId],
        guild_id: GuildId,
    ) -> Result<Permissions, GetPermissionsFailure> {
        let guild_roles = if let Ok(roles) = self.get_roles(guild_id).await {
            roles
        } else {
            return Err(GetPermissionsFailure::FetchFailed);
        };

        get_permissions(member_roles, guild_id, &*guild_roles)
            .ok_or(GetPermissionsFailure::Calculation)
    }

    pub async fn get_member(
        &self,
        guild_id: GuildId,
        user_id: UserId,
    ) -> Result<Arc<CachedMember>, GetMemberFailure> {
        let member = if let Some(cached_member) = self.member_cache.get(&(guild_id, user_id)) {
            cached_member
        } else {
            let fetched_member =
                if let Ok(fetched_member) = guild_id.member(&*self.http().await, user_id).await {
                    let new_cached_member = Arc::new(CachedMember {
                        roles: fetched_member.roles,
                    });
                    self.member_cache
                        .insert((guild_id, user_id), new_cached_member.clone())
                        .await;

                    new_cached_member
                } else {
                    return Err(GetMemberFailure::FetchFailed);
                };

            fetched_member
        };

        Ok(member)
    }

    pub async fn get_roles(
        &self,
        guild_id: GuildId,
    ) -> Result<Arc<HashMap<RoleId, CachedRole>>, GetRolesFailure> {
        let roles = if let Some(cached_roles) = self.role_cache.get(&guild_id) {
            cached_roles
        } else {
            let fetched_roles = if let Ok(fetched_roles) = guild_id.roles(&*self.http().await).await
            {
                let new_cached_roles = Arc::new(
                    fetched_roles
                        .into_iter()
                        .map(|(id, role)| {
                            (
                                id,
                                CachedRole {
                                    name: role.name,
                                    permissions: role.permissions,
                                },
                            )
                        })
                        .collect::<HashMap<RoleId, CachedRole>>(),
                );

                self.role_cache
                    .insert(guild_id, new_cached_roles.clone())
                    .await;
                new_cached_roles
            } else {
                return Err(GetRolesFailure::FetchFailed);
            };

            fetched_roles
        };

        Ok(roles)
    }

    pub async fn get_channels(
        &self,
        guild_id: GuildId,
    ) -> Result<Arc<HashMap<ChannelId, CachedChannel>>, GetChannelsFailure> {
        let channels = if let Some(cached_channels) = self.channel_cache.get(&guild_id) {
            cached_channels
        } else {
            let fetched_channels =
                if let Ok(fetched_channels) = guild_id.channels(&*self.http().await).await {
                    let new_cached_channels = Arc::new(
                        fetched_channels
                            .into_iter()
                            .map(|(id, channel)| (id, CachedChannel { name: channel.name }))
                            .collect::<HashMap<ChannelId, CachedChannel>>(),
                    );
                    self.channel_cache
                        .insert(guild_id, new_cached_channels.clone())
                        .await;
                    new_cached_channels
                } else {
                    return Err(GetChannelsFailure::FetchFailed);
                };

            fetched_channels
        };

        Ok(channels)
    }

    pub async fn get_user(&self, user_id: UserId) -> Result<Arc<CachedUser>, GetUserFailure> {
        let user = if let Some(cached_user) = self.user_cache.get(&user_id) {
            cached_user
        } else {
            let fetched_user = if let Ok(fetched_user) = user_id.to_user(&*self.http().await).await
            {
                let new_cached_user = Arc::new(CachedUser {
                    tag: fetched_user.tag(),
                    avatar_url: fetched_user.face(),
                });
                self.user_cache
                    .insert(user_id, new_cached_user.clone())
                    .await;

                new_cached_user
            } else {
                return Err(GetUserFailure::FetchFailed);
            };

            fetched_user
        };

        Ok(user)
    }

    pub async fn get_guild(&self, guild_id: GuildId) -> Result<Arc<CachedGuild>, GetGuildFailure> {
        let guild = if let Some(cached_guild) = self.guild_cache.get(&guild_id) {
            cached_guild
        } else {
            let fetched_guild =
                if let Ok(fetched_guild) = guild_id.to_partial_guild(&*self.http().await).await {
                    let fetched_guild = Arc::new(CachedGuild {
                        name: fetched_guild.name.clone(),
                        icon_url: fetched_guild.icon_url(),
                        owner_id: fetched_guild.owner_id,
                    });

                    self.guild_cache
                        .insert(guild_id, fetched_guild.clone())
                        .await;
                    fetched_guild
                } else {
                    return Err(GetGuildFailure::FetchFailed);
                };

            fetched_guild
        };

        Ok(guild)
    }
}
