use actix_web::{post, web, HttpResponse, Responder};
use serde::{Deserialize, Serialize};
use tracing::error;
use typemap_rev::TypeMap;

use crate::server::generate_token;
use crate::service::user_secret::UserSecretService;
use crate::Config;

#[derive(Serialize, Deserialize)]
pub struct LoginParams {
    code: String,
}

#[post("/login")]
pub async fn login(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    login_params: web::Query<LoginParams>,
) -> impl Responder {
    let user_secrets_service = if let Some(service) = services.get::<UserSecretService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

    let user_id = match user_secrets_service
        .log_in_as_user(login_params.code.clone())
        .await
    {
        Ok(user_id) => user_id,
        Err(err) => {
            error!("failed to log user in through discord oauth! {}", err);
            return HttpResponse::BadRequest().finish();
        }
    };

    if let Some(token) = generate_token(&config.server_secret, user_id) {
        HttpResponse::Ok().json(token)
    } else {
        HttpResponse::InternalServerError().finish()
    }
}
