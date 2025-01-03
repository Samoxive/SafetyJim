use std::error::Error;
use std::marker::PhantomData;
use std::net::{IpAddr, Ipv6Addr, SocketAddr};
use std::num::NonZeroU64;
use std::sync::Arc;

use axum::extract::{FromRef, FromRequestParts};
use axum::extract::{Path, State};
use axum::http::header::{HeaderName, CONTENT_TYPE};
use axum::http::request::Parts;
use axum::http::{Method, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::*;
use axum::{http, Json, Router};
use http::HeaderValue;
use jsonwebtoken::errors::ErrorKind;
use jsonwebtoken::Algorithm;
use jsonwebtoken::Header;
use jsonwebtoken::Validation;
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey};
use lazy_static::lazy_static;
use serde::Deserialize;
use serde::Serialize;
use serenity::all::GuildId;
use serenity::model::id::UserId;
use serenity::model::Permissions;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;
use tracing::error;
use uuid::Uuid;

use crate::database::settings::{PRIVACY_ADMIN_ONLY, PRIVACY_EVERYONE, PRIVACY_STAFF_ONLY};
use crate::discord::util::is_staff;
use crate::server::endpoint::ban::{get_ban, get_bans, update_ban};
use crate::server::endpoint::captcha::{get_captcha_page, submit_captcha};
use crate::server::endpoint::hardban::{get_hardban, get_hardbans, update_hardban};
use crate::server::endpoint::kick::{get_kick, get_kicks, update_kick};
use crate::server::endpoint::login::login;
use crate::server::endpoint::mute::{get_mute, get_mutes, update_mute};
use crate::server::endpoint::self_user::get_self;
use crate::server::endpoint::settings::{get_setting, reset_setting, update_setting};
use crate::server::endpoint::softban::{get_softban, get_softbans, update_softban};
use crate::server::endpoint::warn::{get_warn, get_warns, update_warn};
use crate::service::guild::GuildService;
use crate::service::invalid_uuid::InvalidUUIDService;
use crate::service::setting::SettingService;
use crate::service::watchdog::WatchdogService;
use crate::service::Services;
use crate::util::now;
use crate::{Config, Shutdown};

mod endpoint;
mod model;

lazy_static! {
    static ref VALIDATION: Validation = Validation::new(Algorithm::HS512);
    static ref ENCODING_HEADER: Header = Header::new(Algorithm::HS512);
}

#[derive(Deserialize, Serialize)]
pub struct JwtClaims {
    #[serde(rename = "userId")]
    pub user_id: String,
    pub uuid: Uuid,
    pub exp: u64,
}

pub fn verify_token(secret: &str, token: &str) -> Option<JwtClaims> {
    match decode::<JwtClaims>(
        token,
        &DecodingKey::from_secret(secret.as_bytes()),
        &VALIDATION,
    ) {
        Ok(data) => Some(data.claims),
        Err(error) => match error.kind() {
            ErrorKind::InvalidSignature | ErrorKind::ExpiredSignature => None,
            _ => {
                error!("failed to decode jwt token {:?}", error.kind());
                None
            }
        },
    }
}

pub fn generate_token(secret: &str, user_id: UserId) -> Option<String> {
    let user_id = user_id.to_string();
    let expires_at = now() + 7 * 24 * 60 * 60;
    let uuid = Uuid::new_v4();
    let claims = JwtClaims {
        user_id,
        uuid,
        exp: expires_at,
    };

    match encode(
        &ENCODING_HEADER,
        &claims,
        &EncodingKey::from_secret(secret.as_bytes()),
    ) {
        Ok(token) => Some(token),
        Err(err) => {
            error!("failed to create jwt token {:?}", err);
            None
        }
    }
}

#[derive(Clone, FromRef)]
pub struct AxumState {
    pub config: Arc<Config>,
    pub services: Arc<Services>,
}

pub fn extract_service<T: typemap_rev::TypeMapKey>(
    services: &Services,
) -> Result<&T::Value, (StatusCode, &'static str)> {
    if let Some(service) = services.get::<T>() {
        Ok(service)
    } else {
        let status_code = StatusCode::INTERNAL_SERVER_ERROR;
        Err((status_code, status_code.canonical_reason().unwrap()))
    }
}

pub struct User(pub UserId);

impl FromRequestParts<AxumState> for User {
    type Rejection = StatusCode;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AxumState,
    ) -> Result<Self, Self::Rejection> {
        let token = if let Some(token) = parts.headers.get("token") {
            token.to_str().map_err(|_err| StatusCode::BAD_REQUEST)?
        } else {
            return Err(StatusCode::UNAUTHORIZED);
        };

        let claims = if let Some(claims) = verify_token(&state.config.server_secret, token) {
            claims
        } else {
            return Err(StatusCode::UNAUTHORIZED);
        };

        let invalid_uuid_service =
            extract_service::<InvalidUUIDService>(&state.services).map_err(|err| err.0)?;
        if invalid_uuid_service.is_uuid_invalid(claims.uuid).await {
            Err(StatusCode::UNAUTHORIZED)
        } else {
            match claims.user_id.parse::<NonZeroU64>() {
                Ok(id) => Ok(User(UserId::new(id.get()))),
                Err(_) => {
                    // secret leaked or we have a problem with token generation
                    error!("received a token with valid signature with invalid user id");
                    Err(StatusCode::UNAUTHORIZED)
                }
            }
        }
    }
}

pub struct ModLogEndpointParams<T: ModPermission>(GuildId, PhantomData<T>);

#[derive(Deserialize)]
pub struct GuildPathParams {
    pub guild_id: NonZeroU64,
}

pub trait ModPermission {
    fn permission() -> Permissions;
}

impl<T: ModPermission> FromRequestParts<AxumState> for ModLogEndpointParams<T> {
    type Rejection = Response;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AxumState,
    ) -> Result<Self, Self::Rejection> {
        let is_read = match parts.method {
            Method::GET => true,
            Method::POST => false,
            _ => return Err(StatusCode::METHOD_NOT_ALLOWED.into_response()),
        };

        let Path(GuildPathParams { guild_id }) =
            Path::<GuildPathParams>::from_request_parts(parts, state)
                .await
                .map_err(|_err| StatusCode::INTERNAL_SERVER_ERROR.into_response())?;

        let guild_id = GuildId::new(guild_id.get());

        let User(user_id) = User::from_request_parts(parts, state)
            .await
            .map_err(|err| err.into_response())?;

        let guild_service =
            extract_service::<GuildService>(&state.services).map_err(|err| err.into_response())?;

        let member = guild_service
            .get_member(guild_id, user_id)
            .await
            .map_err(|_err| {
                (
                    StatusCode::FORBIDDEN,
                    Json("This server either doesn't exist or you aren't in it!"),
                )
                    .into_response()
            })?;

        let permissions = guild_service
            .get_permissions(user_id, &member.roles, guild_id)
            .await
            .map_err(|_err| {
                (
                    StatusCode::FORBIDDEN,
                    Json("This server either doesn't exist or you aren't in it!"),
                )
                    .into_response()
            })?;

        let setting_service = extract_service::<SettingService>(&state.services)
            .map_err(|err| err.into_response())?;
        let setting = setting_service.get_setting(guild_id).await;

        let privacy_setting = setting.privacy_mod_log;
        if is_read {
            let is_authorized = if privacy_setting == PRIVACY_EVERYONE {
                true
            } else if privacy_setting == PRIVACY_STAFF_ONLY {
                is_staff(permissions)
            } else if privacy_setting == PRIVACY_ADMIN_ONLY {
                permissions.administrator()
            } else {
                false
            };

            if !is_authorized {
                return Err((
                    StatusCode::FORBIDDEN,
                    Json("Server settings prevent you from viewing private information!"),
                )
                    .into_response());
            }
        } else {
            let required_permission = T::permission();
            let is_authorized = permissions.contains(Permissions::ADMINISTRATOR)
                || permissions.contains(required_permission);

            if !is_authorized {
                return Err((
                    StatusCode::FORBIDDEN,
                    Json("You don't have permissions to change moderator log history!"),
                )
                    .into_response());
            }
        }

        Ok(ModLogEndpointParams(guild_id, PhantomData))
    }
}

async fn root() -> &'static str {
    "Welcome to Safety Jim API."
}

async fn health_check(State(settings): State<Arc<Services>>) -> StatusCode {
    if let Some(service) = settings.get::<WatchdogService>() {
        if service.is_healthy().await {
            StatusCode::OK
        } else {
            StatusCode::INTERNAL_SERVER_ERROR
        }
    } else {
        StatusCode::INTERNAL_SERVER_ERROR
    }
}

async fn shutdown_signal(shutdown: Shutdown) {
    let mut receiver = shutdown.subscribe();

    let _ = receiver.recv().await;
}

pub async fn run_server(
    config: Arc<Config>,
    services: Arc<Services>,
    shutdown: Shutdown,
) -> Result<(), Box<dyn Error>> {
    let port = config.server_port;
    let cors_origin = config.cors_origin.parse::<HeaderValue>()?;
    let state = AxumState { config, services };
    let app = Router::new()
        .route("/", get(root))
        .route("/health_check", get(health_check))
        .route("/@me", get(get_self))
        .route("/login", post(login))
        .route("/guilds/{guild_id}/settings", get(get_setting))
        .route("/guilds/{guild_id}/settings", post(update_setting))
        .route("/guilds/{guild_id}/settings", delete(reset_setting))
        .route("/captcha/{guild_id}/{user_id}", get(get_captcha_page))
        .route("/captcha/{guild_id}/{user_id}", post(submit_captcha))
        .route("/guilds/{guild_id}/bans", get(get_bans))
        .route("/guilds/{guild_id}/bans/{ban_id}", get(get_ban))
        .route("/guilds/{guild_id}/bans/{ban_id}", post(update_ban))
        .route("/guilds/{guild_id}/hardbans", get(get_hardbans))
        .route("/guilds/{guild_id}/hardbans/{hardban_id}", get(get_hardban))
        .route(
            "/guilds/{guild_id}/hardbans/{hardban_id}",
            post(update_hardban),
        )
        .route("/guilds/{guild_id}/kicks", get(get_kicks))
        .route("/guilds/{guild_id}/kicks/{kick_id}", get(get_kick))
        .route("/guilds/{guild_id}/kicks/{kick_id}", post(update_kick))
        .route("/guilds/{guild_id}/mutes", get(get_mutes))
        .route("/guilds/{guild_id}/mutes/{mute_id}", get(get_mute))
        .route("/guilds/{guild_id}/mutes/{mute_id}", post(update_mute))
        .route("/guilds/{guild_id}/softbans", get(get_softbans))
        .route("/guilds/{guild_id}/softbans/{softban_id}", get(get_softban))
        .route(
            "/guilds/{guild_id}/softbans/{softban_id}",
            post(update_softban),
        )
        .route("/guilds/{guild_id}/warns", get(get_warns))
        .route("/guilds/{guild_id}/warns/{warn_id}", get(get_warn))
        .route("/guilds/{guild_id}/warns/{warn_id}", post(update_warn))
        .layer(
            CorsLayer::new()
                .allow_origin(cors_origin)
                .allow_headers([CONTENT_TYPE, HeaderName::from_static("token")])
                .allow_methods([Method::GET, Method::POST, Method::DELETE]),
        )
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let addr = SocketAddr::new(IpAddr::V6(Ipv6Addr::UNSPECIFIED), port);

    let listener = tokio::net::TcpListener::bind(&addr).await?;
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal(shutdown))
        .await
        .unwrap();

    Ok(())
}
