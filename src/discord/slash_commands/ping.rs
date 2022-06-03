use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::bridge::gateway::ShardId;
use serenity::client::Context;
use serenity::model::application::interaction::application_command::ApplicationCommandInteraction;
use serenity::model::application::interaction::{InteractionResponseType, MessageFlags};
use serenity::model::Permissions;
use serenity::utils::Colour;
use tracing::error;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::constants::AVATAR_URL;
use crate::discord::slash_commands::SlashCommand;
use crate::service::shard_statistic::ShardStatisticService;

pub struct PingCommand;

#[async_trait]
impl SlashCommand for PingCommand {
    fn command_name(&self) -> &'static str {
        "ping"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("ping")
            .description("🏓")
            .dm_permission(false)
            .default_member_permissions(Permissions::all())
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        _config: &Config,
        services: &TypeMap,
    ) -> anyhow::Result<()> {
        let shard_id = context.shard_id;
        let shard_info = if let Some(service) = services.get::<ShardStatisticService>() {
            service.get_shard_latency_info(ShardId(shard_id)).await
        } else {
            bail!("couldn't get shard statistic service!");
        };

        interaction
            .create_interaction_response(&context.http, |response| {
                response
                    .kind(InteractionResponseType::ChannelMessageWithSource)
                    .interaction_response_data(|message| {
                        message
                            .embed(|embed| {
                                embed
                                    .author(|author| {
                                        author
                                            .name(format!(
                                                "Safety Jim [{} / {}]",
                                                shard_id, shard_info.total_shard_count
                                            ))
                                            .icon_url(AVATAR_URL)
                                    })
                                    .description(format!(
                                        ":ping_pong: Ping: {}ms",
                                        shard_info.current_shard_latency
                                    ))
                                    .color(Colour::new(0x4286F4))
                            })
                            .flags(MessageFlags::EPHEMERAL)
                    })
            })
            .await
            .map_err(|err| {
                error!("failed to reply to interaction {}", err);
                err
            })?;

        Ok(())
    }
}
