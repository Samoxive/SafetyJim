use crate::server::is_authenticated;
use crate::server::model::guild::GuildModel;
use crate::server::model::self_user::SelfUserModel;
use crate::service::user_secret::UserSecretService;
use crate::Config;
use actix_web::{get, web, HttpResponse, Responder};
use typemap_rev::TypeMap;

#[get("/@me")]
pub async fn get_self(
    config: web::Data<Config>,
    services: web::Data<TypeMap>,
    req: web::HttpRequest,
) -> impl Responder {
    let user_id = if let Some(id) = is_authenticated(&config, &services, &req).await {
        id
    } else {
        return HttpResponse::Unauthorized().finish();
    };

    let user_secret_service = if let Some(service) = services.get::<UserSecretService>() {
        service
    } else {
        return HttpResponse::InternalServerError().finish();
    };

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
        Err(_) => return HttpResponse::Unauthorized().finish(),
    };

    let self_user = match user_secret_service.get_self_user(user_id).await {
        Ok(self_user) => self_user,
        Err(_) => return HttpResponse::Unauthorized().finish(),
    };

    HttpResponse::Ok().json(SelfUserModel {
        id: self_user.id.to_string(),
        name: self_user.tag.clone(),
        avatar_url: self_user.avatar_url.clone(),
        guilds: self_user_guilds,
    })
}
