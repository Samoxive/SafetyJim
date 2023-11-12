use std::sync::Arc;

use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use serenity::all::Permissions;

use crate::server::endpoint::ModLogPaginationParams;
use crate::server::model::hardban::HardbanModel;
use crate::server::{extract_service, ModLogEndpointParams, ModPermission};
use crate::service::hardban::HardbanService;
use crate::service::Services;

pub struct HardbanModPermission;

impl ModPermission for HardbanModPermission {
    fn permission() -> Permissions {
        Permissions::BAN_MEMBERS
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GetHardbansResponse {
    current_page: u32,
    total_pages: u32,
    entries: Vec<HardbanModel>,
}

// /guilds/:guild_id/hardbans
pub async fn get_hardbans(
    State(services): State<Arc<Services>>,
    Query(mod_log_params): Query<ModLogPaginationParams>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<HardbanModPermission>,
) -> Result<Json<GetHardbansResponse>, StatusCode> {
    let hardban_service = extract_service::<HardbanService>(&services).map_err(|err| err.0)?;

    let mut hardbans = vec![];
    let fetched_hardbans = hardban_service
        .fetch_guild_hardbans(guild_id, mod_log_params.page)
        .await;
    for hardban in fetched_hardbans {
        hardbans.push(HardbanModel::from_hardban(&services, &hardban).await);
    }

    let page_count = hardban_service.fetch_guild_hardban_count(guild_id).await / 10 + 1;

    Ok(Json(GetHardbansResponse {
        current_page: mod_log_params.page.get(),
        total_pages: page_count as u32,
        entries: hardbans,
    }))
}

#[derive(Deserialize)]
pub struct HardbanIdParam {
    pub hardban_id: i32,
}

// /guilds/:guild_id/hardbans/:hardban_id
pub async fn get_hardban(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<HardbanModPermission>,
    Path(HardbanIdParam { hardban_id }): Path<HardbanIdParam>,
) -> Result<Json<HardbanModel>, Response> {
    let hardban_service =
        extract_service::<HardbanService>(&services).map_err(|err| err.into_response())?;

    let hardban = if let Some(hardban) = hardban_service.fetch_hardban(hardban_id).await {
        hardban
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Hardban with given id doesn't exist!"),
        )
            .into_response());
    };

    if hardban.guild_id != guild_id.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given hardban id doesn't belong to your guild!"),
        )
            .into_response());
    }

    let hardban_model = HardbanModel::from_hardban(&services, &hardban).await;

    Ok(Json(hardban_model))
}

// /guilds/:guild_id/hardbans/:hardban_id
pub async fn update_hardban(
    State(services): State<Arc<Services>>,
    ModLogEndpointParams(guild_id, _): ModLogEndpointParams<HardbanModPermission>,
    Path(HardbanIdParam { hardban_id }): Path<HardbanIdParam>,
    Json(mut new_hardban): Json<HardbanModel>,
) -> Result<(), Response> {
    new_hardban.reason = new_hardban.reason.trim().to_string();

    let hardban_service =
        extract_service::<HardbanService>(&services).map_err(|err| err.into_response())?;

    let mut hardban = if let Some(hardban) = hardban_service.fetch_hardban(hardban_id).await {
        hardban
    } else {
        return Err((
            StatusCode::NOT_FOUND,
            Json("Hardban with given id doesn't exist!"),
        )
            .into_response());
    };

    if hardban.guild_id != guild_id.get() as i64 {
        return Err((
            StatusCode::FORBIDDEN,
            Json("Given hardban id doesn't belong to your guild!"),
        )
            .into_response());
    }

    if hardban.id != new_hardban.id
        || hardban.user_id.to_string() != new_hardban.user.id
        || hardban.hardban_time != new_hardban.action_time
        || hardban.moderator_user_id.to_string() != new_hardban.moderator_user.id
    {
        return Err((
            StatusCode::BAD_REQUEST,
            Json("Read only properties were modified!"),
        )
            .into_response());
    }

    hardban.reason = if new_hardban.reason.is_empty() {
        "No reason specified".to_string()
    } else {
        new_hardban.reason.clone()
    };

    hardban_service.update_hardban(hardban).await;

    Ok(())
}
