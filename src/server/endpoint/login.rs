use std::sync::Arc;

use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use serde::{Deserialize, Serialize};
use tracing::error;

use crate::config::Config;
use crate::server::{extract_service, generate_token};
use crate::service::user_secret::UserSecretService;
use crate::service::Services;

#[derive(Serialize, Deserialize)]
pub struct LoginParams {
    code: String,
}

// /login
pub async fn login(
    State(services): State<Arc<Services>>,
    State(config): State<Arc<Config>>,
    Query(login_params): Query<LoginParams>,
) -> Result<Json<String>, Response> {
    let user_secrets_service =
        extract_service::<UserSecretService>(&services).map_err(|err| err.into_response())?;

    let user_id = match user_secrets_service.log_in_as_user(login_params.code).await {
        Ok(user_id) => user_id,
        Err(err) => {
            error!("failed to log user in through discord oauth! {}", err);
            return Err(StatusCode::BAD_REQUEST.into_response());
        }
    };

    if let Some(token) = generate_token(&config.server_secret, user_id) {
        Ok(Json(token))
    } else {
        Err(StatusCode::INTERNAL_SERVER_ERROR.into_response())
    }
}
