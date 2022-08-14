use std::num::NonZeroU64;

use actix_web::{get, post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use serenity::model::id::GuildId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::mute::MuteModel;
use crate::server::{
    apply_mod_log_update_checks, apply_private_endpoint_fetch_checks, PrivateEndpointKind,
};
use crate::service::mute::MuteService;
use crate::Config;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GetMutesResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<MuteModel>,
}

#[get("/guilds/{guild_id}/mutes")]
pub async fn get_mutes(
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

    let mute_service = if let Some(service) = services.get::<MuteService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut mutes = vec![];
    let fetched_mutes = mute_service
        .fetch_guild_mutes(guild_id, mod_log_params.page)
        .await;
    for mute in fetched_mutes {
        mutes.push(MuteModel::from_mute(&services, &mute).await);
    }

    let page_count = mute_service.fetch_guild_mute_count(guild_id).await / 10 + 1;

    HttpResponse::Ok().json(GetMutesResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: mutes,
    })
}

#[get("/guilds/{guild_id}/mutes/{mute_id}")]
pub async fn get_mute(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
) -> impl Responder {
    let (guild_id, mute_id) = path.into_inner();
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

    let mute_service = if let Some(service) = services.get::<MuteService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mute = if let Some(mute) = mute_service.fetch_mute(mute_id).await {
        mute
    } else {
        return HttpResponse::NotFound().json("Mute with given id doesn't exist!");
    };

    let mute_model = MuteModel::from_mute(&services, &mute).await;

    HttpResponse::Ok().json(mute_model)
}

#[post("/guilds/{guild_id}/mutes/{mute_id}")]
pub async fn update_mute(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(NonZeroU64, i32)>,
    mut new_mute: web::Json<MuteModel>,
) -> impl Responder {
    let (guild_id, mute_id) = path.into_inner();
    let guild_id = GuildId(guild_id);

    if let Err(response) = apply_mod_log_update_checks(
        &config,
        &services,
        &req,
        guild_id,
        Permissions::MANAGE_ROLES,
    )
    .await
    {
        return response;
    }

    new_mute.reason = new_mute.reason.trim().to_string();

    let mute_service = if let Some(service) = services.get::<MuteService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut mute = if let Some(mute) = mute_service.fetch_mute(mute_id).await {
        mute
    } else {
        return HttpResponse::NotFound().json("Mute with given id doesn't exist!");
    };

    if mute.guild_id != guild_id.0.get() as i64 {
        return HttpResponse::Forbidden().json("Given mute id doesn't belong to your guild!");
    }

    if mute.id != new_mute.id
        || mute.user_id.to_string() != new_mute.user.id
        || mute.mute_time != new_mute.action_time
        || mute.moderator_user_id.to_string() != new_mute.moderator_user.id
    {
        return HttpResponse::BadRequest().json("Read only properties were modified!");
    }

    if mute.unmuted {
        // expired
        if !new_mute.unmuted || // un-expiring the mute
            mute.expire_time != new_mute.expiration_time
        {
            // changing expiration time
            return HttpResponse::BadRequest()
                .json("You can't change expiration property after user has been unmuted.");
        }
    }

    if mute.pardoned && !new_mute.pardoned {
        return HttpResponse::BadRequest().json("You can't un-pardon a mute!");
    }

    mute.expire_time = new_mute.expiration_time;
    mute.expires = new_mute.expiration_time != 0;
    mute.unmuted = new_mute.unmuted;
    mute.reason = if new_mute.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_mute.reason.clone()
    };
    mute.pardoned = new_mute.pardoned;

    mute_service.update_mute(mute).await;

    HttpResponse::Ok().finish()
}
