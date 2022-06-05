use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
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

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("invite")
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
        interaction
            .create_interaction_response(&context.http, |response| {
                response
                    .kind(InteractionResponseType::ChannelMessageWithSource)
                    .interaction_response_data(|message| {
                        message.content("Links:").components(|components| {
                            components.create_action_row(|row| {
                                row.create_button(|button| {
                                    button
                                        .label("Invite Jim!")
                                        .url(JIM_INVITE_LINK)
                                        .style(ButtonStyle::Link)
                                })
                                .create_button(|button| {
                                    button
                                        .label("Join our support server!")
                                        .url(SUPPORT_SERVER_INVITE_LINK)
                                        .style(ButtonStyle::Link)
                                })
                            })
                        })
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
