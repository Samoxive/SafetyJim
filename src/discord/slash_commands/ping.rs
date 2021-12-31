use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::constants::AVATAR_URL;
use crate::discord::slash_commands::SlashCommand;
use crate::service::shard_statistic::ShardStatisticService;
use anyhow::bail;
use serenity::client::bridge::gateway::ShardId;
use serenity::model::interactions::application_command::ApplicationCommandInteraction;
use serenity::model::interactions::InteractionResponseType;
use serenity::model::prelude::InteractionApplicationCommandCallbackDataFlags;
use serenity::utils::Colour;
use tracing::error;

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
            .default_permission(true)
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
                            .create_embed(|embed| {
                                embed
                                    .author(|author| {
                                        author
                                            .name(format!(
                                                "Safety Jim [{} / {}]",
                                                shard_id,
                                                shard_info.total_shard_count
                                            ))
                                            .icon_url(AVATAR_URL)
                                    })
                                    .description(format!(
                                        ":ping_pong: Ping: {}ms",
                                        shard_info.current_shard_latency
                                    ))
                                    .color(Colour::new(0x4286F4))
                            })
                            .flags(InteractionApplicationCommandCallbackDataFlags::EPHEMERAL)
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
