use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{
    CreateApplicationCommand, CreateEmbed, CreateEmbedAuthor, CreateInteractionResponse,
    CreateInteractionResponseData,
};
use serenity::client::bridge::gateway::ShardId;
use serenity::client::Context;
use serenity::model::application::interaction::application_command::ApplicationCommandInteraction;
use serenity::model::application::interaction::InteractionResponseType;
use serenity::model::channel::MessageFlags;
use tracing::error;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::discord::slash_commands::SlashCommand;
use crate::service::shard_statistic::ShardStatisticService;

pub struct PingCommand;

#[async_trait]
impl SlashCommand for PingCommand {
    fn command_name(&self) -> &'static str {
        "ping"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::default()
            .name("ping")
            .description("🏓")
            .dm_permission(false)
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

        let embed = CreateEmbed::default()
            .author(
                CreateEmbedAuthor::default()
                    .name(format!(
                        "Safety Jim [{} / {}]",
                        shard_id, shard_info.total_shard_count
                    ))
                    .icon_url(AVATAR_URL),
            )
            .description(format!(
                ":ping_pong: Ping: {}ms",
                shard_info.current_shard_latency
            ))
            .color(EMBED_COLOR);

        let data = CreateInteractionResponseData::default()
            .flags(MessageFlags::EPHEMERAL)
            .add_embed(embed);

        let response = CreateInteractionResponse::default()
            .kind(InteractionResponseType::ChannelMessageWithSource)
            .interaction_response_data(data);

        interaction
            .create_interaction_response(&context.http, response)
            .await
            .map_err(|err| {
                error!("failed to reply to interaction {}", err);
                err
            })?;

        Ok(())
    }
}
