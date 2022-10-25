use async_trait::async_trait;
use serenity::builder::CreateCommand;
use serenity::client::Context;
use serenity::model::application::command::CommandType;
use serenity::model::application::interaction::application_command::CommandInteraction;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::reply_with_str;

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
        _services: &TypeMap,
    ) -> anyhow::Result<()> {
        reply_with_str(&context.http, interaction, ":melon:").await;

        Ok(())
    }
}
