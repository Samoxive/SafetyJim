use std::num::NonZeroU64;
use actix_web::{get, post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use serenity::model::id::GuildId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::hardban::HardbanModel;
use crate::server::{
    apply_mod_log_update_checks, apply_private_endpoint_fetch_checks, PrivateEndpointKind,
};
use crate::service::hardban::HardbanService;
use crate::Config;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GetHardbansResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<HardbanModel>,
}

#[get("/guilds/{guild_id}/hardbans")]
pub async fn get_hardbans(
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

    let hardban_service = if let Some(service) = services.get::<HardbanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut hardbans = vec![];
    let fetched_hardbans = hardban_service
        .fetch_guild_hardbans(guild_id, mod_log_params.page)
        .await;
    for hardban in fetched_hardbans {
        hardbans.push(HardbanModel::from_hardban(&services, &hardban).await);
    }

    let page_count = hardban_service.fetch_guild_hardban_count(guild_id).await / 10 + 1;

    HttpResponse::Ok().json(GetHardbansResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: hardbans,
    })
}

#[get("/guilds/{guild_id}/hardbans/{hardban_id}")]
pub async fn get_hardban(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
) -> impl Responder {
    let (guild_id, hardban_id) = path.into_inner();
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

    let hardban_service = if let Some(service) = services.get::<HardbanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let hardban = if let Some(hardban) = hardban_service.fetch_hardban(hardban_id).await {
        hardban
    } else {
        return HttpResponse::NotFound().json("Hardban with given id doesn't exist!");
    };

    let hardban_model = HardbanModel::from_hardban(&services, &hardban).await;

    HttpResponse::Ok().json(hardban_model)
}

#[post("/guilds/{guild_id}/hardbans/{hardban_id}")]
pub async fn update_hardban(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
    mut new_hardban: web::Json<HardbanModel>,
) -> impl Responder {
    let (guild_id, hardban_id) = path.into_inner();
    let guild_id = GuildId(guild_id);

    if let Err(response) =
        apply_mod_log_update_checks(&config, &services, &req, guild_id, Permissions::BAN_MEMBERS)
            .await
    {
        return response;
    }

    new_hardban.reason = new_hardban.reason.trim().to_string();

    let hardban_service = if let Some(service) = services.get::<HardbanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut hardban = if let Some(hardban) = hardban_service.fetch_hardban(hardban_id).await {
        hardban
    } else {
        return HttpResponse::NotFound().json("Hardban with given id doesn't exist!");
    };

    if hardban.guild_id != guild_id.0.get() as i64 {
        return HttpResponse::Forbidden().json("Given hardban id doesn't belong to your guild!");
    }

    if hardban.id != new_hardban.id
        || hardban.user_id.to_string() != new_hardban.user.id
        || hardban.hardban_time != new_hardban.action_time
        || hardban.moderator_user_id.to_string() != new_hardban.moderator_user.id
    {
        return HttpResponse::BadRequest().json("Read only properties were modified!");
    }

    hardban.reason = if new_hardban.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_hardban.reason.clone()
    };

    hardban_service.update_hardban(hardban).await;

    HttpResponse::Ok().finish()
}
