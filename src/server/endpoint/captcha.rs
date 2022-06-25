use std::num::NonZeroU64;
use actix_web::{get, post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use serenity::model::id::{GuildId, RoleId, UserId};
use tracing::{error, warn};
use typemap_rev::TypeMap;

use crate::service::guild::GuildService;
use crate::service::setting::SettingService;
use crate::Config;

const CAPTCHA_TEMPLATE: &str = include_str!("captcha.html");
const CAPTCHA_VERIFICATION_URL: &str = "https://google.com/recaptcha/api/siteverify";

#[get("/captcha/{guild_id}/{user_id}")]
pub async fn get_captcha_page(path: web::Path<(u64, u64)>) -> impl Responder {
    // we don't check guild and users' existence in it because
    // it doesn't matter when initially displaying the captcha
    // by skipping the checks we can just shove the static html
    // for every request. maybe in the future we can display
    // guild info above the captcha but ¯\_(ツ)_/¯
    let (_guild_id, _user_id) = path.into_inner();
    HttpResponse::Ok()
        .content_type("text/html")
        .body(CAPTCHA_TEMPLATE)
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

#[post("/captcha/{guild_id}/{user_id}")]
pub async fn submit_captcha(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    path: web::Path<(NonZeroU64, NonZeroU64)>,
    body: web::Form<CaptchaModel>,
) -> impl Responder {
    let (guild_id, user_id) = path.into_inner();
    let guild_id = GuildId(guild_id);
    let user_id = UserId(user_id);

    match validate_captcha_response(&config.recaptcha_secret, &body.g_recaptcha_response).await {
        Ok(true) => (),
        Ok(false) => return HttpResponse::Forbidden().finish(),
        Err(err) => {
            error!("failed to get captcha validation from google {:?}", err);
            return HttpResponse::InternalServerError().finish();
        }
    }

    let setting_service = if let Some(service) = services.get::<SettingService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let guild_service = if let Some(service) = services.get::<GuildService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let _guild = match guild_service.get_guild(guild_id).await {
        Ok(guild) => guild,
        Err(_) => return HttpResponse::NotFound().finish(),
    };

    let setting = setting_service.get_setting(guild_id).await;
    if !setting.join_captcha {
        return HttpResponse::Forbidden().json("This guild doesn't have join captcha enabled!");
    }

    let role_id = if let Some(id) = setting.holding_room_role_id {
        match NonZeroU64::new(id as u64) {
            Some(id) => {
                RoleId(id)
            },
            _ => {
                warn!("found setting with invalid holding room id! {:?}", setting);
                return HttpResponse::BadRequest().json("Holding room role is invalid!")
            }
        }
    } else {
        return HttpResponse::BadRequest().json("Holding room role isn't set up!");
    };

    let _ = guild_service
        .http()
        .await
        .add_member_role(
            guild_id.0.get(),
            user_id.0.get(),
            role_id.0.get(),
            Some("Taking member out of holding room because of completed captcha challenge"),
        )
        .await;

    HttpResponse::Ok().body("You have been approved to join! You can close this window.")
}
