use crate::constants::{JIM_ID, JIM_ID_AND_TAG};
use crate::database::settings::{get_action_duration_for_auto_mod_action, Setting};
use crate::discord::message_processors::MessageProcessor;
use crate::discord::util::{execute_mod_action, is_staff, SerenityErrorExt};
use crate::service::guild::GuildService;
use anyhow::bail;
use async_trait::async_trait;
use serenity::client::Context;
use serenity::model::id::{ChannelId, GuildId, MessageId};
use serenity::model::user::User;
use serenity::model::Permissions;
use tracing::error;
use typemap_rev::TypeMap;

const REASON: &str = "Sending invite links";

pub struct InviteLinkProcessor;

#[async_trait]
impl MessageProcessor for InviteLinkProcessor {
    async fn handle_message(
        &self,
        context: &Context,
        message_content: &str,
        guild_id: GuildId,
        channel_id: ChannelId,
        message_id: MessageId,
        author: &User,
        permissions: Permissions,
        setting: &Setting,
        services: &TypeMap,
    ) -> anyhow::Result<bool> {
        if !setting.invite_link_remover {
            return Ok(false);
        }

        if is_staff(permissions) {
            return Ok(false);
        }

        if !message_content.contains("discord.gg/") {
            return Ok(false);
        }

        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            bail!("couldn't get guild service!");
        };

        let guild = if let Ok(guild) = guild_service.get_guild(guild_id).await {
            guild
        } else {
            bail!("couldn't get guild name!");
        };

        let duration = get_action_duration_for_auto_mod_action(
            setting.invite_link_remover_action,
            setting.invite_link_remover_action_duration_type,
            setting.invite_link_remover_action_duration,
        );

        match channel_id.delete_message(&context.http, message_id).await {
            Ok(_) => {
                execute_mod_action(
                    setting.invite_link_remover_action,
                    &*context.http,
                    guild_id,
                    &guild.name,
                    setting,
                    services,
                    Some(channel_id),
                    JIM_ID,
                    JIM_ID_AND_TAG,
                    author,
                    REASON.into(),
                    duration,
                    0,
                )
                .await;
                Ok(true)
            }
            Err(err) => {
                match err.discord_error_code() {
                    Some(50013) => (),
                    _ => {
                        error!("failed to delete message for censorship {}", err);
                    }
                }
                Ok(false)
            }
        }
    }
}
