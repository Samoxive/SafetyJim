use async_trait::async_trait;
use serenity::builder::{
    CreateActionRow, CreateApplicationCommand, CreateButton, CreateComponents,
    CreateInteractionResponse, CreateInteractionResponseData,
};
use serenity::client::Context;
use serenity::model::application::component::ButtonStyle;
use serenity::model::application::interaction::application_command::ApplicationCommandInteraction;
use serenity::model::application::interaction::InteractionResponseType;
use tracing::error;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;

const SUPPORT_SERVER_INVITE_LINK: &str = "https://discord.io/safetyjim";
const JIM_INVITE_LINK: &str = "https://discord.com/api/oauth2/authorize?client_id=881152939530534913&permissions=0&scope=bot%20applications.commands";

pub struct InviteCommand;

#[async_trait]
impl SlashCommand for InviteCommand {
    fn command_name(&self) -> &'static str {
        "invite"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::new("invite")
            .description("displays links to invite Jim and get support")
            .dm_permission(false)
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        _config: &Config,
        _services: &TypeMap,
    ) -> anyhow::Result<()> {
        let components = CreateComponents::default().add_action_row(
            CreateActionRow::default()
                .add_button(
                    CreateButton::default()
                        .label("Invite Jim!")
                        .url(JIM_INVITE_LINK)
                        .style(ButtonStyle::Link),
                )
                .add_button(
                    CreateButton::default()
                        .label("Join our support server!")
                        .url(SUPPORT_SERVER_INVITE_LINK)
                        .style(ButtonStyle::Link),
                ),
        );

        let data = CreateInteractionResponseData::default()
            .content("Links:")
            .components(components);

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
