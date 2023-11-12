use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use serenity::model::Permissions;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::warn::WarnModel;
use crate::server::{extract_service, ModLogEndpointParams, ModPermission};
use crate::service::warn::WarnService;
use crate::service::Services;

pub struct WarnModPermission;

impl ModPermission for WarnModPermission {
    fn permission() -> Permissions {
        Permissions::KICK_MEMBERS
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GetWarnsResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<WarnModel>,
}

// /guilds/:guild_id/warns
pub async fn get_warns(
    State(services): State<Arc<Services>>,
    Query(mod_log_params): Query<ModLogPaginationParams>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<WarnModPermission>,
) -> Result<Json<GetWarnsResponse>, StatusCode> {
    let warn_service = extract_service::<WarnService>(&services).map_err(|err| err.0)?;

    let mut warns = vec![];
    let fetched_warns = warn_service
        .fetch_guild_warns(guild_id, mod_log_params.page)
        .await;
    for warn in fetched_warns {
        warns.push(WarnModel::from_warn(&services, &warn).await);
    }

    let page_count = warn_service.fetch_guild_warn_count(guild_id).await / 10 + 1;

    Ok(Json(GetWarnsResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: warns,
    }))
}

#[derive(Deserialize)]
pub struct WarnIdParam {
    pub warn_id: i32,
}

// /guilds/:guild_id/warns/:warn_id
pub async fn get_warn(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<WarnModPermission>,
    Path(WarnIdParam { warn_id }): Path<WarnIdParam>,
) -> Result<Json<WarnModel>, Response> {
    let warn_service =
        extract_service::<WarnService>(&services).map_err(|err| err.into_response())?;

    let warn = if let Some(warn) = warn_service.fetch_warn(warn_id).await {
        warn
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Warn with given id doesn't exist!"),
        )
            .into_response());
    };

    if warn.guild_id != guild_id.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given warn id doesn't belong to your guild!"),
        )
            .into_response());
    }

    let warn_model = WarnModel::from_warn(&services, &warn).await;

    Ok(Json(warn_model))
}

// /guilds/:guild_id/warns/:warn_id
pub async fn update_warn(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<WarnModPermission>,
    Path(WarnIdParam { warn_id }): Path<WarnIdParam>,
    Json(mut new_warn): Json<WarnModel>,
) -> Result<(), Response> {
    new_warn.reason = new_warn.reason.trim().to_string();

    let warn_service =
        extract_service::<WarnService>(&services).map_err(|err| err.into_response())?;

    let mut warn = if let Some(warn) = warn_service.fetch_warn(warn_id).await {
        warn
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Warn with given id doesn't exist!"),
        )
            .into_response());
    };

    if warn.guild_id != guild_id.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given warn id doesn't belong to your guild!"),
        )
            .into_response());
    }

    if warn.id != new_warn.id
        || warn.user_id.to_string() != new_warn.user.id
        || warn.warn_time != new_warn.action_time
        || warn.moderator_user_id.to_string() != new_warn.moderator_user.id
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Read only properties were modified!"),
        )
            .into_response());
    }

    if warn.pardoned && !new_warn.pardoned {
        return Err((StatusCode::BAD_REQUEST, Json("You can't un-pardon a warn!")).into_response());
    }

    warn.reason = if new_warn.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_warn.reason.clone()
    };
    warn.pardoned = new_warn.pardoned;

    warn_service.update_warn(warn).await;

    Ok(())
}
