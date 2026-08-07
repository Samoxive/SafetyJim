use std::num::NonZeroU64;
use std::sync::Arc;

use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{Html, IntoResponse, Response};
use axum::{Form, Json};
use serde::{Deserialize, Serialize};
use serenity::all::{GuildId, RoleId, UserId};
use tracing::{error, warn};

use crate::config::Config;
use crate::server::extract_service;
use crate::service::guild::GuildService;
use crate::service::setting::SettingService;
use crate::service::Services;

const CAPTCHA_TEMPLATE: &str = include_str!("captcha.html");
const CAPTCHA_VERIFICATION_URL: &str = "https://google.com/recaptcha/api/siteverify";

#[derive(Deserialize)]
pub struct CaptchaParams {
    guild_id: NonZeroU64,
    user_id: NonZeroU64,
}

// /captcha/:guild_id/:user_id
pub async fn get_captcha_page(_path: Path<CaptchaParams>) -> Html<&'static str> {
    Html(CAPTCHA_TEMPLATE)
}

#[derive(Serialize, Deserialize)]
pub struct CaptchaModel {
    #[serde(rename = "g-recaptcha-response")]
    g_recaptcha_response: String,
}

async fn validate_captcha_response(secret: &str, response_id: &str) -> anyhow::Result<bool> {
    // client can be cached for repeated uses, however captcha verification
    // is used rare enough for this implementation to suffice for now.
    Ok(reqwest::Client::builder()
        .user_agent("Safety Jim")
        .build()?
        .post(CAPTCHA_VERIFICATION_URL)
        .query(&[("secret", secret), ("response", response_id)])
        .send()
        .await
        .map_err(|err| {
            error!("failed to validate captcha response {}", err);
            err
        })?
        .json::<serde_json::Value>()
        .await
        .map_err(|err| {
            error!(
                "failed to parse captcha validation response from google {}",
                err
            );
            err
        })?
        .get("success")
        .and_then(|success| success.as_bool())
        .unwrap_or(false))
}

// /captcha/:guild_id/:user_id
pub async fn submit_captcha(
    State(settings): State<Arc<Services>>,
    State(config): State<Arc<Config>>,
    Path(CaptchaParams { guild_id, user_id }): Path<CaptchaParams>,
    body: Form<CaptchaModel>,
) -> Result<Json<&'static str>, Response> {
    let guild_id = GuildId::new(guild_id.get());
    let user_id = UserId::new(user_id.get());

    match validate_captcha_response(&config.recaptcha_secret, &body.g_recaptcha_response).await {
        Ok(true) => (),
        Ok(false) => return Err(StatusCode::FORBIDDEN.into_response()),
        Err(err) => {
            error!("failed to get captcha validation from google {:?}", err);
            return Err(StatusCode::INTERNAL_SERVER_ERROR.into_response());
        }
    }

    let setting_service =
        extract_service::<SettingService>(&settings).map_err(|err| err.into_response())?;
    let guild_service =
        extract_service::<GuildService>(&settings).map_err(|err| err.into_response())?;

    let _guild = match guild_service.get_guild(guild_id).await {
        Ok(guild) => guild,
        Err(_) => return Err(StatusCode::NOT_FOUND.into_response()),
    };

    let setting = setting_service.get_setting(guild_id).await;
    if !setting.join_captcha {
        return Err((
            StatusCode::FORBIDDEN,
            Json("This guild doesn't have join captcha enabled!"),
        )
            .into_response());
    }

    let role_id = if let Some(id) = setting.holding_room_role_id {
        match NonZeroU64::new(id as u64) {
            Some(id) => RoleId::new(id.get()),
            _ => {
                warn!("found setting with invalid holding room id! {:?}", setting);
                return Err((
                    StatusCode::BAD_REQUEST,
                    Json("Holding room role is invalid!"),
                )
                    .into_response());
            }
        }
    } else {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Holding room role isn't set up!"),
        )
            .into_response());
    };

    let _ = guild_service
        .http()
        .await
        .add_member_role(
            guild_id,
            user_id,
            role_id,
            Some("Taking member out of holding room because of completed captcha challenge"),
        )
        .await;

    Ok(Json(
        "You have been approved to join! You can close this window.",
    ))
}
