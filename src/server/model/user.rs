use crate::service::guild::GuildService;
use serde::{Deserialize, Serialize};
use serenity::model::id::UserId;
use typemap_rev::TypeMap;

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UserModel {
    pub id: String,
    pub username: String,
    pub avatar_url: String,
}

impl UserModel {
    pub async fn from_id(services: &TypeMap, user_id: UserId) -> UserModel {
        let user_model = if let Some(guild_service) = services.get::<GuildService>() {
            guild_service
                .get_user(user_id)
                .await
                .ok()
                .map(|user| UserModel {
                    id: user_id.to_string(),
                    username: user.tag.clone(),
                    avatar_url: user.avatar_url.clone(),
                })
        } else {
            None
        };

        if let Some(user_model) = user_model {
            user_model
        } else {
            UserModel {
                id: user_id.to_string(),
                username: "Deleted User#0000".to_string(),
                avatar_url: "https://cdn.discordapp.com/embed/avatars/1.png".to_string(),
            }
        }
    }
}
