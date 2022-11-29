use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use serenity::all::Permissions;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::ban::BanModel;
use crate::server::{extract_service, ModLogEndpointParams, ModPermission};
use crate::service::ban::BanService;
use crate::service::Services;

pub struct BanModPermission;

impl ModPermission for BanModPermission {
    fn permission() -> Permissions {
        Permissions::BAN_MEMBERS
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GetBansResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<BanModel>,
}

// /guilds/:guild_id/bans
pub async fn get_bans(
    State(services): State<Arc<Services>>,
    Query(mod_log_params): Query<ModLogPaginationParams>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<BanModPermission>,
) -> Result<Json<GetBansResponse>, StatusCode> {
    let ban_service = extract_service::<BanService>(&services).map_err(|err| err.0)?;

    let mut bans = vec![];
    let fetched_bans = ban_service
        .fetch_guild_bans(guild_id, mod_log_params.page)
        .await;
    for ban in fetched_bans {
        bans.push(BanModel::from_ban(&services, &ban).await);
    }

    let page_count = ban_service.fetch_guild_ban_count(guild_id).await / 10 + 1;

    Ok(Json(GetBansResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: bans,
    }))
}

#[derive(Deserialize)]
pub struct BanIdParam {
    pub ban_id: i32,
}

// /guilds/:guild_id/bans/:ban_id
pub async fn get_ban(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<BanModPermission>,
    Path(BanIdParam { ban_id }): Path<BanIdParam>,
) -> Result<Json<BanModel>, Response> {
    let ban_service =
        extract_service::<BanService>(&services).map_err(|err| err.into_response())?;

    let ban = if let Some(ban) = ban_service.fetch_ban(ban_id).await {
        ban
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Ban with given id doesn't exist!"),
        )
            .into_response());
    };

    if ban.guild_id != guild_id.0.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given ban id doesn't belong to your guild!"),
        )
            .into_response());
    }

    let ban_model = BanModel::from_ban(&services, &ban).await;

    Ok(Json(ban_model))
}

// /guilds/:guild_id/bans/:ban_id
pub async fn update_ban(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<BanModPermission>,
    Path(BanIdParam { ban_id }): Path<BanIdParam>,
    Json(mut new_ban): Json<BanModel>,
) -> Result<(), Response> {
    new_ban.reason = new_ban.reason.trim().to_string();

    let ban_service =
        extract_service::<BanService>(&services).map_err(|err| err.into_response())?;

    let mut ban = if let Some(ban) = ban_service.fetch_ban(ban_id).await {
        ban
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Ban with given id doesn't exist!"),
        )
            .into_response());
    };

    if ban.guild_id != guild_id.0.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given ban id doesn't belong to your guild!"),
        )
            .into_response());
    }

    if ban.id != new_ban.id
        || ban.user_id.to_string() != new_ban.user.id
        || ban.ban_time != new_ban.action_time
        || ban.moderator_user_id.to_string() != new_ban.moderator_user.id
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Read only properties were modified!"),
        )
            .into_response());
    }

    if ban.unbanned {
        // expired
        if !new_ban.unbanned || // un-expiring the ban
            ban.expire_time != new_ban.expiration_time
        {
            // changing expiration time
            return Err((
                StatusCode::BAD_REQUEST,
                Json("You can't change expiration property after user has been unbanned."),
            )
                .into_response());
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

    Ok(())
}
