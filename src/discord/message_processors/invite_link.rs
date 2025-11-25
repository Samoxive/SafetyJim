use anyhow::bail;
use async_trait::async_trait;
use serenity::all::{Context, GenericChannelId};
use serenity::model::id::{GuildId, MessageId};
use serenity::model::user::User;
use serenity::model::Permissions;
use tracing::error;

use crate::constants::{JIM_ID, JIM_ID_AND_TAG};
use crate::database::settings::{get_action_duration_for_auto_mod_action, Setting};
use crate::discord::message_processors::MessageProcessor;
use crate::discord::util::{execute_mod_action, is_staff, SerenityErrorExt};
use crate::service::guild::GuildService;
use crate::service::Services;

const REASON: &str = "Sending invite links";

pub struct InviteLinkProcessor;

#[async_trait]
impl MessageProcessor for InviteLinkProcessor {
    async fn handle_message(
        &self,
        context: &Context,
        message_content: &str,
        guild_id: GuildId,
        channel_id: GenericChannelId,
        message_id: MessageId,
        author: &User,
        permissions: Permissions,
        setting: &Setting,
        services: &Services,
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

        match channel_id
            .delete_message(&context.http, message_id, Some(REASON))
            .await
        {
            Ok(_) => {
                execute_mod_action(
                    setting.invite_link_remover_action,
                    &context.http,
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
