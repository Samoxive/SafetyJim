mod endpoint;
mod model;

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
use crate::service::guild::{CachedMember, GuildService};
use crate::service::invalid_uuid::InvalidUUIDService;
use crate::service::setting::SettingService;
use crate::util::now;
use crate::Config;
use actix_cors::Cors;
use actix_web::http::header::{HeaderName, CONTENT_TYPE};
use actix_web::{get, web, App, HttpRequest, HttpResponse, HttpServer, Responder};
use jsonwebtoken::errors::ErrorKind;
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use lazy_static::lazy_static;
use serde::{Deserialize, Serialize};
use serenity::model::id::{GuildId, UserId};
use serenity::model::Permissions;
use std::error::Error;
use std::net::Ipv4Addr;
use std::sync::Arc;
use tracing::error;
use typemap_rev::TypeMap;
use uuid::Uuid;

lazy_static! {
    static ref VALIDATION: Validation = Validation {
        algorithms: vec![Algorithm::HS512],
        ..Validation::default()
    };
    static ref ENCODING_HEADER: Header = Header::new(Algorithm::HS512);
}

#[derive(Deserialize, Serialize)]
struct JwtClaims {
    #[serde(rename = "userId")]
    user_id: String,
    uuid: Uuid,
    exp: u64,
}

fn verify_token(secret: &str, token: &str) -> Option<JwtClaims> {
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

fn generate_token(secret: &str, user_id: UserId) -> Option<String> {
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

async fn is_authenticated(
    config: &Config,
    services: &TypeMap,
    req: &web::HttpRequest,
) -> Option<UserId> {
    let token = req
        .headers()
        .get(HeaderName::from_static("token")) // optimize into constant?
        .map(|value| value.to_str())
        .map(|result| result.ok())
        .flatten()?;

    let claims = verify_token(&config.server_secret, token)?;

    let invalid_uuid_service = services.get::<InvalidUUIDService>()?;

    if invalid_uuid_service.is_uuid_invalid(claims.uuid).await {
        None
    } else {
        match claims.user_id.parse::<u64>() {
            Ok(id) => Some(UserId(id)),
            Err(_) => {
                // secret leaked or we have a problem with token generation
                error!("received a token with valid signature with invalid user id");
                None
            }
        }
    }
}

pub async fn check_authentication(
    config: &Config,
    services: &TypeMap,
    req: &web::HttpRequest,
) -> Result<UserId, HttpResponse> {
    is_authenticated(config, services, req)
        .await
        .ok_or_else(|| HttpResponse::Unauthorized().json("Invalid token!"))
}

pub enum PrivateEndpointKind {
    ModLog,
    Settings,
}

async fn is_authorized_to_view_private_endpoint(
    services: &TypeMap,
    guild_id: GuildId,
    permissions: Permissions,
    endpoint_kind: PrivateEndpointKind,
) -> bool {
    let setting_service = if let Some(service) = services.get::<SettingService>() {
        service
    } else {
        return false;
    };

    let setting = setting_service.get_setting(guild_id).await;
    let privacy_setting = match endpoint_kind {
        PrivateEndpointKind::Settings => setting.privacy_settings,
        PrivateEndpointKind::ModLog => setting.privacy_mod_log,
    };

    if privacy_setting == PRIVACY_EVERYONE {
        true
    } else if privacy_setting == PRIVACY_STAFF_ONLY {
        is_staff(permissions)
    } else if privacy_setting == PRIVACY_ADMIN_ONLY {
        permissions.administrator()
    } else {
        false
    }
}

pub async fn check_authorization_to_view_private_endpoint(
    services: &TypeMap,
    guild_id: GuildId,
    permissions: Permissions,
    endpoint_kind: PrivateEndpointKind,
) -> Option<HttpResponse> {
    if is_authorized_to_view_private_endpoint(services, guild_id, permissions, endpoint_kind).await
    {
        None
    } else {
        Some(
            HttpResponse::Forbidden()
                .json("Server settings prevent you from viewing private information!"),
        )
    }
}

pub async fn is_guild_endpoint(
    services: &TypeMap,
    guild_id: GuildId,
    user_id: UserId,
) -> Option<(Arc<CachedMember>, Permissions)> {
    let guild_service = services.get::<GuildService>()?;

    let member = guild_service.get_member(guild_id, user_id).await.ok()?;

    let permissions =
        if let Ok(permissions) = guild_service.get_permissions(&member.roles, guild_id).await {
            permissions
        } else {
            return None;
        };

    Some((member, permissions))
}

pub async fn check_guild_endpoint(
    services: &TypeMap,
    guild_id: GuildId,
    user_id: UserId,
) -> Result<(Arc<CachedMember>, Permissions), HttpResponse> {
    is_guild_endpoint(services, guild_id, user_id)
        .await
        .ok_or_else(|| {
            HttpResponse::Forbidden().json("This server either doesn't exist or you aren't in it!")
        })
}

pub async fn apply_private_endpoint_fetch_checks(
    config: &Config,
    services: &TypeMap,
    req: &HttpRequest,
    guild_id: GuildId,
    endpoint_kind: PrivateEndpointKind,
) -> Result<(), HttpResponse> {
    let user_id = match check_authentication(config, services, req).await {
        Ok(user_id) => user_id,
        Err(response) => return Err(response),
    };

    let (_, permissions) = match check_guild_endpoint(services, guild_id, user_id).await {
        Ok((member, permissions)) => (member, permissions),
        Err(response) => return Err(response),
    };

    if let Some(response) =
        check_authorization_to_view_private_endpoint(services, guild_id, permissions, endpoint_kind)
            .await
    {
        return Err(response);
    }

    Ok(())
}

pub async fn apply_mod_log_update_checks(
    config: &Config,
    services: &TypeMap,
    req: &HttpRequest,
    guild_id: GuildId,
    required_permission: Permissions,
) -> Result<(), HttpResponse> {
    let user_id = match check_authentication(config, services, req).await {
        Ok(user_id) => user_id,
        Err(response) => return Err(response),
    };

    let (_, permissions) = match check_guild_endpoint(services, guild_id, user_id).await {
        Ok((member, permissions)) => (member, permissions),
        Err(response) => return Err(response),
    };

    if permissions.contains(Permissions::ADMINISTRATOR) || permissions.contains(required_permission)
    {
        Ok(())
    } else {
        Err(HttpResponse::Forbidden()
            .json("You don't have permissions to change moderator log history!"))
    }
}

pub async fn apply_setting_update_checks(
    config: &Config,
    services: &TypeMap,
    req: &HttpRequest,
    guild_id: GuildId,
) -> Result<(), HttpResponse> {
    let user_id = match check_authentication(config, services, req).await {
        Ok(user_id) => user_id,
        Err(response) => return Err(response),
    };

    let (_, permissions) = match check_guild_endpoint(services, guild_id, user_id).await {
        Ok((member, permissions)) => (member, permissions),
        Err(response) => return Err(response),
    };

    if permissions.contains(Permissions::ADMINISTRATOR) {
        Ok(())
    } else {
        Err(HttpResponse::Forbidden().json("You don't have permissions to change server settings!"))
    }
}

#[get("/")]
async fn index() -> impl Responder {
    "Welcome to Safety Jim API."
}

pub async fn run_server(config: Arc<Config>, services: Arc<TypeMap>) -> Result<(), Box<dyn Error>> {
    HttpServer::new(move || {
        App::new()
            .app_data(web::Data::from(config.clone()))
            .app_data(web::Data::from(services.clone()))
            .service(index)
            .service(get_self)
            .service(get_bans)
            .service(get_ban)
            .service(update_ban)
            .service(get_captcha_page)
            .service(submit_captcha)
            .service(get_hardbans)
            .service(get_hardban)
            .service(update_hardban)
            .service(get_kicks)
            .service(get_kick)
            .service(update_kick)
            .service(login)
            .service(get_mutes)
            .service(get_mute)
            .service(update_mute)
            .service(get_setting)
            .service(update_setting)
            .service(reset_setting)
            .service(get_softbans)
            .service(get_softban)
            .service(update_softban)
            .service(get_warns)
            .service(get_warn)
            .service(update_warn)
            .wrap(
                Cors::default()
                    .allowed_origin(&config.cors_origin)
                    // needed for captcha endpoint, actix-cors doesn't actually check if request is for CORS
                    .allowed_origin(&config.self_url)
                    .allowed_methods(vec!["GET", "POST", "DELETE"])
                    .allowed_headers(vec![CONTENT_TYPE, HeaderName::from_static("token")])
                    .max_age(None),
            )
    })
    .bind((Ipv4Addr::LOCALHOST, 8080))?
    .run()
    .await?;

    Ok(())
}
