use actix_web::{get, post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use serenity::model::id::GuildId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::ban::BanModel;
use crate::server::{
    apply_mod_log_update_checks, apply_private_endpoint_fetch_checks, PrivateEndpointKind,
};
use crate::service::ban::BanService;
use crate::Config;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct GetBansResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<BanModel>,
}

#[get("/guilds/{guild_id}/bans")]
pub async fn get_bans(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    guild_id: web::Path<u64>,
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

    let ban_service = if let Some(service) = services.get::<BanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut bans = vec![];
    let fetched_bans = ban_service
        .fetch_guild_bans(guild_id, mod_log_params.page)
        .await;
    for ban in fetched_bans {
        bans.push(BanModel::from_ban(&services, &ban).await);
    }

    let page_count = ban_service.fetch_guild_ban_count(guild_id).await / 10 + 1;

    HttpResponse::Ok().json(GetBansResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: bans,
    })
}

#[get("/guilds/{guild_id}/bans/{ban_id}")]
pub async fn get_ban(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(u64, i32)>,
) -> impl Responder {
    let (guild_id, ban_id) = path.into_inner();
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

    let ban_service = if let Some(service) = services.get::<BanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let ban = if let Some(ban) = ban_service.fetch_ban(ban_id).await {
        ban
    } else {
        return HttpResponse::NotFound().json("Ban with given id doesn't exist!");
    };

    let ban_model = BanModel::from_ban(&services, &ban).await;

    HttpResponse::Ok().json(ban_model)
}

#[post("/guilds/{guild_id}/bans/{ban_id}")]
pub async fn update_ban(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: actix_web::HttpRequest,
    path: web::Path<(u64, i32)>,
    mut new_ban: web::Json<BanModel>,
) -> impl Responder {
    let (guild_id, ban_id) = path.into_inner();
    let guild_id = GuildId(guild_id);

    if let Err(response) =
        apply_mod_log_update_checks(&config, &services, &req, guild_id, Permissions::BAN_MEMBERS)
            .await
    {
        return response;
    }

    new_ban.reason = new_ban.reason.trim().to_string();

    let ban_service = if let Some(service) = services.get::<BanService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let mut ban = if let Some(ban) = ban_service.fetch_ban(ban_id).await {
        ban
    } else {
        return HttpResponse::NotFound().json("Ban with given id doesn't exist!");
    };

    if ban.guild_id != guild_id.0 as i64 {
        return HttpResponse::Forbidden().json("Given ban id doesn't belong to your guild!");
    }

    if ban.id != new_ban.id
        || ban.user_id.to_string() != new_ban.user.id
        || ban.ban_time != new_ban.action_time
        || ban.moderator_user_id.to_string() != new_ban.moderator_user.id
    {
        return HttpResponse::BadRequest().json("Read only properties were modified!");
    }

    if ban.unbanned {
        // expired
        if !new_ban.unbanned || // un-expiring the ban
            ban.expire_time != new_ban.expiration_time
        {
            // changing expiration time
            return HttpResponse::BadRequest()
                .json("You can't change expiration property after user has been unbanned.");
        }
    }

    ban.expire_time = new_ban.expiration_time;
    ban.expires = new_ban.expiration_time != 0;
    ban.unbanned = new_ban.unbanned;
    ban.reason = if new_ban.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_ban.reason.clone()
    };

    ban_service.update_ban(ban).await;

    HttpResponse::Ok().finish()
}
