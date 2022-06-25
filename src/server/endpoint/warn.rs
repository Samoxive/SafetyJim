use std::num::NonZeroU64;
use actix_web::{get, post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use serenity::model::id::GuildId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::warn::WarnModel;
use crate::server::{
    apply_mod_log_update_checks, apply_private_endpoint_fetch_checks, PrivateEndpointKind,
};
use crate::service::warn::WarnService;
use crate::Config;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GetWarnsResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<WarnModel>,
}

#[get("/guilds/{guild_id}/warns")]
pub async fn get_warns(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    guild_id: web::Path<NonZeroU64>,
    mod_log_params: web::Query<ModLogPaginationParams>,
) -> impl Responder {
    let guild_id = GuildId(guild_id.into_inner());

    if let Err(response) = apply_private_endpoint_fetch_checks(
        &config,
        &services,
        &req,
        guild_id,
        PrivateEndpointKind::ModLog,
    )
    .await
    {
        return response;
    }

    let warn_service = if let Some(service) = services.get::<WarnService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut warns = vec![];
    let fetched_warns = warn_service
        .fetch_guild_warns(guild_id, mod_log_params.page)
        .await;
    for warn in fetched_warns {
        warns.push(WarnModel::from_warn(&services, &warn).await);
    }

    let page_count = warn_service.fetch_guild_warn_count(guild_id).await / 10 + 1;

    HttpResponse::Ok().json(GetWarnsResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: warns,
    })
}

#[get("/guilds/{guild_id}/warns/{warn_id}")]
pub async fn get_warn(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
) -> impl Responder {
    let (guild_id, warn_id) = path.into_inner();
    let guild_id = GuildId(guild_id);

    if let Err(response) = apply_private_endpoint_fetch_checks(
        &config,
        &services,
        &req,
        guild_id,
        PrivateEndpointKind::ModLog,
    )
    .await
    {
        return response;
    }

    let warn_service = if let Some(service) = services.get::<WarnService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let warn = if let Some(warn) = warn_service.fetch_warn(warn_id).await {
        warn
    } else {
        return HttpResponse::NotFound().json("Warn with given id doesn't exist!");
    };

    let warn_model = WarnModel::from_warn(&services, &warn).await;

    HttpResponse::Ok().json(warn_model)
}

#[post("/guilds/{guild_id}/warns/{warn_id}")]
pub async fn update_warn(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
    mut new_warn: web::Json<WarnModel>,
) -> impl Responder {
    let (guild_id, warn_id) = path.into_inner();
    let guild_id = GuildId(guild_id);

    if let Err(response) = apply_mod_log_update_checks(
        &config,
        &services,
        &req,
        guild_id,
        Permissions::KICK_MEMBERS,
    )
    .await
    {
        return response;
    }

    new_warn.reason = new_warn.reason.trim().to_string();

    let warn_service = if let Some(service) = services.get::<WarnService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut warn = if let Some(warn) = warn_service.fetch_warn(warn_id).await {
        warn
    } else {
        return HttpResponse::NotFound().json("Warn with given id doesn't exist!");
    };

    if warn.guild_id != guild_id.0.get() as i64 {
        return HttpResponse::Forbidden().json("Given warn id doesn't belong to your guild!");
    }

    if warn.id != new_warn.id
        || warn.user_id.to_string() != new_warn.user.id
        || warn.warn_time != new_warn.action_time
        || warn.moderator_user_id.to_string() != new_warn.moderator_user.id
    {
        return HttpResponse::BadRequest().json("Read only properties were modified!");
    }

    if warn.pardoned && !new_warn.pardoned {
        return HttpResponse::BadRequest().json("You can't un-pardon a warn!");
    }

    warn.reason = if new_warn.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_warn.reason.clone()
    };
    warn.pardoned = new_warn.pardoned;

    warn_service.update_warn(warn).await;

    HttpResponse::Ok().finish()
}
