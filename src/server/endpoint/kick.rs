use std::num::NonZeroU64;

use actix_web::{get, post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use serenity::model::id::GuildId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::kick::KickModel;
use crate::server::{
    apply_mod_log_update_checks, apply_private_endpoint_fetch_checks, PrivateEndpointKind,
};
use crate::service::kick::KickService;
use crate::Config;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GetKicksResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<KickModel>,
}

#[get("/guilds/{guild_id}/kicks")]
pub async fn get_kicks(
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

    let kick_service = if let Some(service) = services.get::<KickService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut kicks = vec![];
    let fetched_kicks = kick_service
        .fetch_guild_kicks(guild_id, mod_log_params.page)
        .await;
    for kick in fetched_kicks {
        kicks.push(KickModel::from_kick(&services, &kick).await);
    }

    let page_count = kick_service.fetch_guild_kick_count(guild_id).await / 10 + 1;

    HttpResponse::Ok().json(GetKicksResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: kicks,
    })
}

#[get("/guilds/{guild_id}/kicks/{kick_id}")]
pub async fn get_kick(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
) -> impl Responder {
    let (guild_id, kick_id) = path.into_inner();
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

    let kick_service = if let Some(service) = services.get::<KickService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let kick = if let Some(kick) = kick_service.fetch_kick(kick_id).await {
        kick
    } else {
        return HttpResponse::NotFound().json("Kick with given id doesn't exist!");
    };

    let kick_model = KickModel::from_kick(&services, &kick).await;

    HttpResponse::Ok().json(kick_model)
}

#[post("/guilds/{guild_id}/kicks/{kick_id}")]
pub async fn update_kick(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
    mut new_kick: web::Json<KickModel>,
) -> impl Responder {
    let (guild_id, kick_id) = path.into_inner();
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

    new_kick.reason = new_kick.reason.trim().to_string();

    let kick_service = if let Some(service) = services.get::<KickService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut kick = if let Some(kick) = kick_service.fetch_kick(kick_id).await {
        kick
    } else {
        return HttpResponse::NotFound().json("Kick with given id doesn't exist!");
    };

    if kick.guild_id != guild_id.0.get() as i64 {
        return HttpResponse::Forbidden().json("Given kick id doesn't belong to your guild!");
    }

    if kick.id != new_kick.id
        || kick.user_id.to_string() != new_kick.user.id
        || kick.kick_time != new_kick.action_time
        || kick.moderator_user_id.to_string() != new_kick.moderator_user.id
    {
        return HttpResponse::BadRequest().json("Read only properties were modified!");
    }

    if kick.pardoned && !new_kick.pardoned {
        return HttpResponse::BadRequest().json("You can't un-pardon a kick!");
    }

    kick.reason = if new_kick.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_kick.reason.clone()
    };
    kick.pardoned = new_kick.pardoned;

    kick_service.update_kick(kick).await;

    HttpResponse::Ok().finish()
}
