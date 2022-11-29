use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use serenity::all::Permissions;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::kick::KickModel;
use crate::server::{extract_service, ModLogEndpointParams, ModPermission};
use crate::service::kick::KickService;
use crate::service::Services;

pub struct KickModPermission;

impl ModPermission for KickModPermission {
    fn permission() -> Permissions {
        Permissions::KICK_MEMBERS
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GetKicksResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<KickModel>,
}

// /guilds/:guild_id/kicks
pub async fn get_kicks(
    State(services): State<Arc<Services>>,
    Query(mod_log_params): Query<ModLogPaginationParams>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<KickModPermission>,
) -> Result<Json<GetKicksResponse>, StatusCode> {
    let kick_service = extract_service::<KickService>(&services).map_err(|err| err.0)?;

    let mut kicks = vec![];
    let fetched_kicks = kick_service
        .fetch_guild_kicks(guild_id, mod_log_params.page)
        .await;
    for kick in fetched_kicks {
        kicks.push(KickModel::from_kick(&services, &kick).await);
    }

    let page_count = kick_service.fetch_guild_kick_count(guild_id).await / 10 + 1;

    Ok(Json(GetKicksResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: kicks,
    }))
}

#[derive(Deserialize)]
pub struct KickIdParam {
    pub kick_id: i32,
}

// /guilds/:guild_id/kicks/:kick_id
pub async fn get_kick(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<KickModPermission>,
    Path(KickIdParam { kick_id }): Path<KickIdParam>,
) -> Result<Json<KickModel>, Response> {
    let kick_service =
        extract_service::<KickService>(&services).map_err(|err| err.into_response())?;

    let kick = if let Some(kick) = kick_service.fetch_kick(kick_id).await {
        kick
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Kick with given id doesn't exist!"),
        )
            .into_response());
    };

    if kick.guild_id != guild_id.0.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given kick id doesn't belong to your guild!"),
        )
            .into_response());
    }

    let kick_model = KickModel::from_kick(&services, &kick).await;

    Ok(Json(kick_model))
}

// /guilds/:guild_id/kicks/:kick_id
pub async fn update_kick(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<KickModPermission>,
    Path(KickIdParam { kick_id }): Path<KickIdParam>,
    Json(mut new_kick): Json<KickModel>,
) -> Result<(), Response> {
    new_kick.reason = new_kick.reason.trim().to_string();

    let kick_service =
        extract_service::<KickService>(&services).map_err(|err| err.into_response())?;

    let mut kick = if let Some(kick) = kick_service.fetch_kick(kick_id).await {
        kick
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Kick with given id doesn't exist!"),
        )
            .into_response());
    };

    if kick.guild_id != guild_id.0.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given kick id doesn't belong to your guild!"),
        )
            .into_response());
    }

    if kick.id != new_kick.id
        || kick.user_id.to_string() != new_kick.user.id
        || kick.kick_time != new_kick.action_time
        || kick.moderator_user_id.to_string() != new_kick.moderator_user.id
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Read only properties were modified!"),
        )
            .into_response());
    }

    if kick.pardoned && !new_kick.pardoned {
        return Err((StatusCode::BAD_REQUEST, Json("You can't un-pardon a kick!")).into_response());
    }

    kick.reason = if new_kick.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_kick.reason.clone()
    };
    kick.pardoned = new_kick.pardoned;

    kick_service.update_kick(kick).await;

    Ok(())
}
