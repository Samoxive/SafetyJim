use async_trait::async_trait;
use serenity::all::{CommandInteraction, CommandType};
use serenity::builder::{
    CreateActionRow, CreateButton, CreateCommand, CreateInteractionResponse,
    CreateInteractionResponseMessage,
};
use serenity::client::Context;
use tracing::error;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;
use crate::service::Services;

const SUPPORT_SERVER_INVITE_LINK: &str = "https://discord.io/safetyjim";
const JIM_INVITE_LINK: &str = "https://discord.com/api/oauth2/authorize?client_id=881152939530534913&permissions=0&scope=bot%20applications.commands";

pub struct InviteCommand;

#[async_trait]
impl SlashCommand for InviteCommand {
    fn command_name(&self) -> &'static str {
        "invite"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("invite")
            .kind(CommandType::ChatInput)
            .description("displays links to invite Jim and get support")
            .dm_permission(false)
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        _config: &Config,
        _services: &Services,
    ) -> anyhow::Result<()> {
        let components = vec![CreateActionRow::Buttons(vec![
            CreateButton::new_link("Invite Jim!").label(JIM_INVITE_LINK),
            CreateButton::new_link("Join our support server!").label(SUPPORT_SERVER_INVITE_LINK),
        ])];

        let data = CreateInteractionResponseMessage::new()
            .content("Links:")
            .components(components);

        let response = CreateInteractionResponse::Message(data);

        interaction
            .create_response(&context.http, response)
            .await
            .map_err(|err| {
                error!("failed to reply to interaction {}", err);
                err
            })?;

        Ok(())
    }
}
