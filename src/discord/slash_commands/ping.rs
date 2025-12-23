use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{CommandInteraction, CommandType, InstallationContext, InteractionContext};
use serenity::builder::{CreateCommand, CreateEmbed, CreateEmbedAuthor};

use crate::config::Config;
use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::reply_to_interaction_embed;
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
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        _config: &Config,
        _services: &Services,
    ) -> anyhow::Result<()> {
        let shard_id = context.shard_id;

        let embed = CreateEmbed::default()
            .author(
                CreateEmbedAuthor::new(format!(
                    "Safety Jim [{}]",
                    shard_id
                ))
                .icon_url(AVATAR_URL),
            )
            .description(":ping_pong:")
            .color(EMBED_COLOR);

        reply_to_interaction_embed(&context.http, interaction, embed, true).await;

        Ok(())
    }
}
