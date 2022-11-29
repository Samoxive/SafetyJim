use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use serenity::model::Permissions;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::softban::SoftbanModel;
use crate::server::{extract_service, ModLogEndpointParams, ModPermission};
use crate::service::softban::SoftbanService;
use crate::service::Services;

pub struct SoftbanModPermission;

impl ModPermission for SoftbanModPermission {
    fn permission() -> Permissions {
        Permissions::BAN_MEMBERS
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GetSoftbansResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<SoftbanModel>,
}

// /guilds/:guild_id/softbans
pub async fn get_softbans(
    State(services): State<Arc<Services>>,
    Query(mod_log_params): Query<ModLogPaginationParams>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<SoftbanModPermission>,
) -> Result<Json<GetSoftbansResponse>, StatusCode> {
    let softban_service = extract_service::<SoftbanService>(&services).map_err(|err| err.0)?;

    let mut softbans = vec![];
    let fetched_softbans = softban_service
        .fetch_guild_softbans(guild_id, mod_log_params.page)
        .await;
    for softban in fetched_softbans {
        softbans.push(SoftbanModel::from_softban(&services, &softban).await);
    }

    let page_count = softban_service.fetch_guild_softban_count(guild_id).await / 10 + 1;

    Ok(Json(GetSoftbansResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: softbans,
    }))
}

#[derive(Deserialize)]
pub struct SoftbanIdParam {
    pub softban_id: i32,
}

// /guilds/:guild_id/softbans/:softban_id
pub async fn get_softban(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<SoftbanModPermission>,
    Path(SoftbanIdParam { softban_id }): Path<SoftbanIdParam>,
) -> Result<Json<SoftbanModel>, Response> {
    let softban_service =
        extract_service::<SoftbanService>(&services).map_err(|err| err.into_response())?;

    let softban = if let Some(softban) = softban_service.fetch_softban(softban_id).await {
        softban
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Softban with given id doesn't exist!"),
        )
            .into_response());
    };

    if softban.guild_id != guild_id.0.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given softban id doesn't belong to your guild!"),
        )
            .into_response());
    }

    let softban_model = SoftbanModel::from_softban(&services, &softban).await;

    Ok(Json(softban_model))
}

// /guilds/:guild_id/softbans/:softban_id
pub async fn update_softban(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<SoftbanModPermission>,
    Path(SoftbanIdParam { softban_id }): Path<SoftbanIdParam>,
    Json(mut new_softban): Json<SoftbanModel>,
) -> Result<(), Response> {
    new_softban.reason = new_softban.reason.trim().to_string();

    let softban_service =
        extract_service::<SoftbanService>(&services).map_err(|err| err.into_response())?;

    let mut softban = if let Some(softban) = softban_service.fetch_softban(softban_id).await {
        softban
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Softban with given id doesn't exist!"),
        )
            .into_response());
    };

    if softban.guild_id != guild_id.0.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given softban id doesn't belong to your guild!"),
        )
            .into_response());
    }

    if softban.id != new_softban.id
        || softban.user_id.to_string() != new_softban.user.id
        || softban.softban_time != new_softban.action_time
        || softban.moderator_user_id.to_string() != new_softban.moderator_user.id
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Read only properties were modified!"),
        )
            .into_response());
    }

    if softban.pardoned && !new_softban.pardoned {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("You can't un-pardon a softban!"),
        )
            .into_response());
    }

    softban.reason = if new_softban.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_softban.reason.clone()
    };
    softban.pardoned = new_softban.pardoned;

    softban_service.update_softban(softban).await;

    Ok(())
}
