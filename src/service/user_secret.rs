use std::num::ParseIntError;
use std::sync::Arc;
use std::time::Duration;

use anyhow::bail;
use moka::future::{Cache, CacheBuilder};
use reqwest::{Client, ClientBuilder};
use serde::{Deserialize, Serialize};
use serenity::model::id::{GuildId, UserId};
use tracing::error;
use typemap_rev::{TypeMap, TypeMapKey};

use crate::constants::{DISCORD_API_BASE, DISCORD_CDN_BASE};
use crate::database::user_secrets::{UserSecret, UserSecretsRepository};
use crate::service::guild_statistic::GuildStatisticService;
use crate::Config;

impl TypeMapKey for UserSecretService {
    type Value = UserSecretService;
}

pub struct UserSecretService {
    pub config: Arc<Config>,
    pub repository: UserSecretsRepository,
    pub self_user_cache: Cache<UserId, Arc<SelfUser>>,
    pub self_user_guilds_cache: Cache<UserId, Arc<Vec<SelfGuild>>>,
    client: Client,
}

#[derive(Serialize, Deserialize)]
struct AccessTokenResponse {
    access_token: String,
    token_type: String,
    expires_in: i32,
    refresh_token: String,
    scope: String,
}

#[derive(Serialize, Deserialize)]
struct CodeAuthorizationRequestForm<'a> {
    client_id: &'a str,
    client_secret: &'a str,
    grant_type: &'a str,
    code: &'a str,
    redirect_uri: &'a str,
}

#[derive(Serialize, Deserialize)]
struct SelfUserResponse {
    id: String,
    username: String,
    discriminator: String,
    avatar: Option<String>,
}

#[derive(Serialize, Deserialize)]
pub struct SelfGuildResponse {
    pub id: String,
    pub name: String,
    pub icon: Option<String>,
}

pub struct SelfUser {
    pub id: UserId,
    pub tag: String,
    pub avatar_url: String,
}

pub struct SelfGuild {
    pub id: GuildId,
    pub name: String,
    pub icon_url: String,
}

fn generate_default_user_avatar_url(discriminator: &str) -> String {
    let discriminator = discriminator.parse::<u16>().unwrap_or(0);

    format!(
        "{}/embed/avatars/{}.png",
        DISCORD_CDN_BASE,
        discriminator % 5u16
    )
}

fn generate_user_avatar_url(self_user_response: &SelfUserResponse) -> String {
    self_user_response
        .avatar
        .as_ref()
        .map(|avatar| {
            format!(
                "{}/avatars/{}/{}.png",
                DISCORD_CDN_BASE, &self_user_response.id, avatar
            )
        })
        .unwrap_or_else(|| generate_default_user_avatar_url(&self_user_response.discriminator))
}

fn generate_guild_icon_url(guild: &SelfGuildResponse) -> String {
    if let Some(icon) = &guild.icon {
        let ext = if icon.starts_with("a_") {
            "gif"
        } else {
            "webp"
        };
        format!("{}/icons/{}/{}.{}", DISCORD_CDN_BASE, guild.id, icon, ext)
    } else {
        "".to_string()
    }
}

async fn fetch_self_user(client: &Client, access_token: &str) -> anyhow::Result<SelfUser> {
    let response = client
        .get(format!("{}/api/users/@me", DISCORD_API_BASE))
        .header("Authorization", format!("Bearer {}", access_token))
        .send()
        .await
        .map_err(|err| {
            error!("failed to fetch self user via discord oauth {}", err);
            err
        })?
        .json::<SelfUserResponse>()
        .await
        .map_err(|err| {
            error!("failed to parse self user response from discord {}", err);
            err
        })?;

    let user_id = match response.id.parse::<u64>() {
        Ok(id) => UserId(id),
        Err(err) => {
            error!("failed to parse self user id from discord {}", err);
            bail!(err);
        }
    };

    Ok(SelfUser {
        id: user_id,
        tag: format!("{}#{}", response.username, response.discriminator),
        avatar_url: generate_user_avatar_url(&response),
    })
}

pub async fn fetch_self_user_guilds(
    client: &Client,
    access_token: &str,
) -> anyhow::Result<Vec<SelfGuild>> {
    let response = client
        .get(format!("{}/api/users/@me/guilds", DISCORD_API_BASE))
        .header("Authorization", format!("Bearer {}", access_token))
        .send()
        .await
        .map_err(|err| {
            error!("failed to fetch self user guilds via discord oauth {}", err);
            err
        })?
        .json::<Vec<SelfGuildResponse>>()
        .await
        .map_err(|err| {
            error!(
                "failed to parse self user guilds response from discord {}",
                err
            );
            err
        })?;

    match response
        .into_iter()
        .map(|guild| {
            let guild_id = match guild.id.parse::<u64>() {
                Ok(id) => GuildId(id),
                Err(err) => {
                    error!("failed to parse self user guild id from discord {}", err);
                    return Err(err);
                }
            };

            Ok(SelfGuild {
                id: guild_id,
                icon_url: generate_guild_icon_url(&guild),
                name: guild.name,
            })
        })
        .collect::<Result<Vec<SelfGuild>, ParseIntError>>()
    {
        Ok(guilds) => Ok(guilds),
        Err(err) => bail!(err),
    }
}

impl UserSecretService {
    pub fn new(config: Arc<Config>, repository: UserSecretsRepository) -> UserSecretService {
        UserSecretService {
            config,
            repository,
            self_user_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(60))
                .time_to_live(Duration::from_secs(60))
                .build(),
            self_user_guilds_cache: CacheBuilder::new(100)
                .time_to_idle(Duration::from_secs(60))
                .time_to_live(Duration::from_secs(60))
                .build(),
            client: ClientBuilder::new()
                .user_agent("Safety Jim")
                .build()
                .expect("constructing client failed"),
        }
    }

    pub async fn log_in_as_user(&self, code: String) -> anyhow::Result<UserId> {
        let form_body = CodeAuthorizationRequestForm {
            client_id: &self.config.oauth_client_id,
            client_secret: &self.config.oauth_client_secret,
            grant_type: "authorization_code",
            code: &code,
            redirect_uri: &self.config.oauth_redirect_uri,
        };

        let response = self
            .client
            .post(format!("{}/api/oauth2/token", DISCORD_API_BASE))
            .form(&form_body)
            .send()
            .await
            .map_err(|err| {
                error!("failed to login as user via discord oauth {}", err);
                err
            })?
            .json::<AccessTokenResponse>()
            .await
            .map_err(|err| {
                error!("failed to parse oauth login response {}", err);
                err
            })?;

        if response.scope != "identify guilds" {
            error!("discord returned a different scope! {}", response.scope);
            bail!("discord oauth login failed");
        }

        // we don't use UserSecretService#fetch_self_user as we need to check the given access_token asap
        let self_user = fetch_self_user(&self.client, &response.access_token).await?;

        self.repository
            .upsert_user_secret(UserSecret {
                user_id: self_user.id.0 as i64,
                access_token: response.access_token,
            })
            .await
            .map_err(|err| {
                error!("failed to upsert user secrets {:?}", err);
                err
            })?;

        let user_id = self_user.id;

        self.self_user_cache
            .insert(user_id, Arc::new(self_user))
            .await;

        Ok(user_id)
    }

    pub async fn get_self_user(&self, user_id: UserId) -> anyhow::Result<Arc<SelfUser>> {
        let self_user = if let Some(cached_self_user) = self.self_user_cache.get(&user_id) {
            cached_self_user
        } else {
            let fetched_self_user = match self.fetch_self_user(user_id).await {
                Ok(user) => user,
                Err(err) => bail!(err),
            };

            let fetched_self_user = Arc::new(fetched_self_user);

            self.self_user_cache
                .insert(user_id, fetched_self_user.clone())
                .await;

            fetched_self_user
        };

        Ok(self_user)
    }

    pub async fn fetch_self_user(&self, user_id: UserId) -> anyhow::Result<SelfUser> {
        let access_token = match self.repository.fetch_user_secret(user_id.0 as i64).await {
            Ok(Some(secret)) => secret.access_token,
            Ok(None) => {
                error!("user's secrets don't exist yet their token does!");
                bail!("user secrets not found");
            }
            Err(err) => {
                error!("failed to fetch access token {:?}", err);
                bail!(err);
            }
        };

        let self_user = match fetch_self_user(&self.client, &access_token).await {
            Ok(self_user) => self_user,
            Err(err) => {
                error!("failed to fetch self user {:?}", err);
                bail!(err);
            }
        };

        Ok(self_user)
    }

    pub async fn get_self_user_guilds(
        &self,
        services: &TypeMap,
        user_id: UserId,
    ) -> anyhow::Result<Arc<Vec<SelfGuild>>> {
        let self_user_guilds =
            if let Some(cached_self_user_guilds) = self.self_user_guilds_cache.get(&user_id) {
                cached_self_user_guilds
            } else {
                let fetched_self_user_guilds =
                    Arc::new(self.fetch_self_user_guilds(services, user_id).await?);

                self.self_user_guilds_cache
                    .insert(user_id, fetched_self_user_guilds.clone())
                    .await;

                fetched_self_user_guilds
            };

        Ok(self_user_guilds)
    }

    pub async fn fetch_self_user_guilds(
        &self,
        services: &TypeMap,
        user_id: UserId,
    ) -> anyhow::Result<Vec<SelfGuild>> {
        let access_token = match self.repository.fetch_user_secret(user_id.0 as i64).await {
            Ok(Some(secret)) => secret.access_token,
            Ok(None) => {
                error!("user's secrets don't exist yet their token does!");
                bail!("user secrets not found");
            }
            Err(err) => {
                error!("failed to fetch access token {:?}", err);
                bail!(err);
            }
        };

        let self_user_guilds = match fetch_self_user_guilds(&self.client, &access_token).await {
            Ok(guilds) => guilds,
            Err(err) => bail!("failed to fetch self user guilds {:?}", err),
        };

        let filtered_guild_ids =
            if let Some(guild_statistic_service) = services.get::<GuildStatisticService>() {
                let self_guild_ids = self_user_guilds
                    .iter()
                    .map(|guild| guild.id)
                    .collect::<Vec<GuildId>>();

                guild_statistic_service
                    .filter_known_guilds(&self_guild_ids)
                    .await
            } else {
                bail!("failed to get guild statistic service!");
            };

        Ok(self_user_guilds
            .into_iter()
            .filter(|user_guild| {
                filtered_guild_ids
                    .iter()
                    .any(|known_guild| *known_guild == user_guild.id)
            })
            .collect())
    }
}
