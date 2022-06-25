use std::num::NonZeroU64;
use actix_web::{get, post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use serenity::model::id::GuildId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::softban::SoftbanModel;
use crate::server::{
    apply_mod_log_update_checks, apply_private_endpoint_fetch_checks, PrivateEndpointKind,
};
use crate::service::softban::SoftbanService;
use crate::Config;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GetSoftbansResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<SoftbanModel>,
}

#[get("/guilds/{guild_id}/softbans")]
pub async fn get_softbans(
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

    let softban_service = if let Some(service) = services.get::<SoftbanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut softbans = vec![];
    let fetched_softbans = softban_service
        .fetch_guild_softbans(guild_id, mod_log_params.page)
        .await;
    for softban in fetched_softbans {
        softbans.push(SoftbanModel::from_softban(&services, &softban).await);
    }

    let page_count = softban_service.fetch_guild_softban_count(guild_id).await / 10 + 1;

    HttpResponse::Ok().json(GetSoftbansResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: softbans,
    })
}

#[get("/guilds/{guild_id}/softbans/{softban_id}")]
pub async fn get_softban(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
) -> impl Responder {
    let (guild_id, softban_id) = path.into_inner();
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

    let softban_service = if let Some(service) = services.get::<SoftbanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let softban = if let Some(softban) = softban_service.fetch_softban(softban_id).await {
        softban
    } else {
        return HttpResponse::NotFound().json("Softban with given id doesn't exist!");
    };

    let softban_model = SoftbanModel::from_softban(&services, &softban).await;

    HttpResponse::Ok().json(softban_model)
}

#[post("/guilds/{guild_id}/softbans/{softban_id}")]
pub async fn update_softban(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
    mut new_softban: web::Json<SoftbanModel>,
) -> impl Responder {
    let (guild_id, softban_id) = path.into_inner();
    let guild_id = GuildId(guild_id);

    if let Err(response) =
        apply_mod_log_update_checks(&config, &services, &req, guild_id, Permissions::BAN_MEMBERS)
            .await
    {
        return response;
    }

    new_softban.reason = new_softban.reason.trim().to_string();

    let softban_service = if let Some(service) = services.get::<SoftbanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut softban = if let Some(softban) = softban_service.fetch_softban(softban_id).await {
        softban
    } else {
        return HttpResponse::NotFound().json("Softban with given id doesn't exist!");
    };

    if softban.guild_id != guild_id.0.get() as i64 {
        return HttpResponse::Forbidden().json("Given softban id doesn't belong to your guild!");
    }

    if softban.id != new_softban.id
        || softban.user_id.to_string() != new_softban.user.id
        || softban.softban_time != new_softban.action_time
        || softban.moderator_user_id.to_string() != new_softban.moderator_user.id
    {
        return HttpResponse::BadRequest().json("Read only properties were modified!");
    }

    if softban.pardoned && !new_softban.pardoned {
        return HttpResponse::BadRequest().json("You can't un-pardon a softban!");
    }

    softban.reason = if new_softban.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_softban.reason.clone()
    };
    softban.pardoned = new_softban.pardoned;

    softban_service.update_softban(softban).await;

    HttpResponse::Ok().finish()
}
