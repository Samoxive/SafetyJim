use anyhow::bail;
use async_trait::async_trait;
use serenity::all::{CommandInteraction, CommandType};
use serenity::builder::{CreateCommand, CreateEmbed, CreateEmbedAuthor};
use serenity::client::Context;

use crate::config::Config;
use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::reply_to_interaction_embed;
use crate::service::shard_statistic::ShardStatisticService;
use crate::service::Services;

pub struct PingCommand;

#[async_trait]
impl SlashCommand for PingCommand {
    fn command_name(&self) -> &'static str {
        "ping"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("ping")
            .kind(CommandType::ChatInput)
            .description("🏓")
            .dm_permission(false)
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        _config: &Config,
        services: &Services,
    ) -> anyhow::Result<()> {
        let shard_id = context.shard_id;
        let shard_info = if let Some(service) = services.get::<ShardStatisticService>() {
            service.get_shard_latency_info(shard_id).await
        } else {
            bail!("couldn't get shard statistic service!");
        };

        let embed = CreateEmbed::default()
            .author(
                CreateEmbedAuthor::new(format!(
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

        reply_to_interaction_embed(&context.http, interaction, embed, true).await;

        Ok(())
    }
}
