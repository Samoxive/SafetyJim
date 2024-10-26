use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{CommandInteraction, CommandType, InstallationContext, InteractionContext};
use serenity::builder::CreateCommand;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::reply_to_interaction_str;
use crate::service::Services;

pub struct MeloCommand;

#[async_trait]
impl SlashCommand for MeloCommand {
    fn command_name(&self) -> &'static str {
        "melo"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("melo")
            .kind(CommandType::ChatInput)
            .description("melo.")
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
        let _ = reply_to_interaction_str(&context.http, interaction, ":melon:", false).await;

        Ok(())
    }
}
