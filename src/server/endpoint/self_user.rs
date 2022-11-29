use std::sync::Arc;

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;

use crate::server::model::guild::GuildModel;
use crate::server::model::self_user::SelfUserModel;
use crate::server::{extract_service, User};
use crate::service::user_secret::UserSecretService;
use crate::service::Services;

// /@me
pub async fn get_self(
    State(services): State<Arc<Services>>,
    User(user_id): User,
) -> Result<Json<SelfUserModel>, Response> {
    let user_secret_service =
        extract_service::<UserSecretService>(&services).map_err(|err| err.into_response())?;
    let self_user_guilds: Vec<GuildModel> = match user_secret_service
        .get_self_user_guilds(&services, user_id)
        .await
    {
        Ok(guilds) => guilds
            .iter()
            .map(|guild| GuildModel {
                id: guild.id.to_string(),
                name: guild.name.clone(),
                icon_url: guild.icon_url.clone(),
            })
            .collect(),
        Err(_) => return Err(StatusCode::UNAUTHORIZED.into_response()),
    };

    let self_user = match user_secret_service.get_self_user(user_id).await {
        Ok(self_user) => self_user,
        Err(_) => return Err(StatusCode::UNAUTHORIZED.into_response()),
    };

    Ok(Json(SelfUserModel {
        id: self_user.id.to_string(),
        name: self_user.tag.clone(),
        avatar_url: self_user.avatar_url.clone(),
        guilds: self_user_guilds,
    }))
}
