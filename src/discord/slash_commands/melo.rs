use async_trait::async_trait;
use serenity::all::{CommandInteraction, CommandType};
use serenity::builder::CreateCommand;
use serenity::client::Context;

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
            .dm_permission(false)
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
