use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use serenity::all::Permissions;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::mute::MuteModel;
use crate::server::{extract_service, ModLogEndpointParams, ModPermission};
use crate::service::mute::MuteService;
use crate::service::Services;

pub struct MuteModPermission;

impl ModPermission for MuteModPermission {
    fn permission() -> Permissions {
        Permissions::MANAGE_ROLES
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GetMutesResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<MuteModel>,
}

// /guilds/:guild_id/mutes
pub async fn get_mutes(
    State(services): State<Arc<Services>>,
    Query(mod_log_params): Query<ModLogPaginationParams>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<MuteModPermission>,
) -> Result<Json<GetMutesResponse>, StatusCode> {
    let mute_service = extract_service::<MuteService>(&services).map_err(|err| err.0)?;

    let mut mutes = vec![];
    let fetched_mutes = mute_service
        .fetch_guild_mutes(guild_id, mod_log_params.page)
        .await;
    for mute in fetched_mutes {
        mutes.push(MuteModel::from_mute(&services, &mute).await);
    }

    let page_count = mute_service.fetch_guild_mute_count(guild_id).await / 10 + 1;

    Ok(Json(GetMutesResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: mutes,
    }))
}

#[derive(Deserialize)]
pub struct MuteIdParam {
    pub mute_id: i32,
}

// /guilds/:guild_id/mutes/:mute_id
pub async fn get_mute(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<MuteModPermission>,
    Path(MuteIdParam { mute_id }): Path<MuteIdParam>,
) -> Result<Json<MuteModel>, Response> {
    let mute_service =
        extract_service::<MuteService>(&services).map_err(|err| err.into_response())?;

    let mute = if let Some(mute) = mute_service.fetch_mute(mute_id).await {
        mute
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Mute with given id doesn't exist!"),
        )
            .into_response());
    };

    if mute.guild_id != guild_id.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given mute id doesn't belong to your guild!"),
        )
            .into_response());
    }

    let mute_model = MuteModel::from_mute(&services, &mute).await;

    Ok(Json(mute_model))
}

// /guilds/:guild_id/mutes/:mute_id
pub async fn update_mute(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<MuteModPermission>,
    Path(MuteIdParam { mute_id }): Path<MuteIdParam>,
    Json(mut new_mute): Json<MuteModel>,
) -> Result<(), Response> {
    new_mute.reason = new_mute.reason.trim().to_string();

    let mute_service =
        extract_service::<MuteService>(&services).map_err(|err| err.into_response())?;

    let mut mute = if let Some(mute) = mute_service.fetch_mute(mute_id).await {
        mute
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Mute with given id doesn't exist!"),
        )
            .into_response());
    };

    if mute.guild_id != guild_id.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given mute id doesn't belong to your guild!"),
        )
            .into_response());
    }

    if mute.id != new_mute.id
        || mute.user_id.to_string() != new_mute.user.id
        || mute.mute_time != new_mute.action_time
        || mute.moderator_user_id.to_string() != new_mute.moderator_user.id
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Read only properties were modified!"),
        )
            .into_response());
    }

    if mute.unmuted {
        // expired
        if !new_mute.unmuted || // un-expiring the mute
            mute.expire_time != new_mute.expiration_time
        {
            // changing expiration time
            return Err((
                StatusCode::BAD_REQUEST,
                Json("You can't change expiration property after user has been unmuted."),
            )
                .into_response());
        }
    }

    if mute.pardoned && !new_mute.pardoned {
        return Err((StatusCode::BAD_REQUEST, Json("You can't un-pardon a mute!")).into_response());
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

    Ok(())
}
