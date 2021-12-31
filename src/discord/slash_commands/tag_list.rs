use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::ApplicationCommandInteraction;

use crate::config::Config;
use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, verify_guild_slash_command, GuildSlashCommandInteraction,
};
use crate::service::tag::TagService;
use anyhow::bail;
use serenity::model::interactions::{
    InteractionApplicationCommandCallbackDataFlags, InteractionResponseType,
};
use tracing::error;
use typemap_rev::TypeMap;

pub struct TagListCommand;

#[async_trait]
impl SlashCommand for TagListCommand {
    fn command_name(&self) -> &'static str {
        "tag-list"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("tag-list")
            .description("lists previously registered tags")
            .default_permission(true)
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        _config: &Config,
        services: &TypeMap,
    ) -> anyhow::Result<()> {
        let GuildSlashCommandInteraction {
            guild_id,
            member: _,
            permissions: _,
        } = verify_guild_slash_command(interaction)?;

        let tag_service = if let Some(service) = services.get::<TagService>() {
            service
        } else {
            bail!("couldn't get tag service!");
        };

        let tags = tag_service.get_tag_names(guild_id).await;

        if tags.is_empty() {
            invisible_failure_reply(&context.http, interaction, "No tags have been added yet!")
                .await;
        } else {
            let tags_str = tags
                .iter()
                .map(|tag| format!("\u{2022} {}", tag))
                .collect::<Vec<String>>()
                .join("\n");

            interaction
                .create_interaction_response(&context.http, |response| {
                    response
                        .kind(InteractionResponseType::ChannelMessageWithSource)
                        .interaction_response_data(|message| {
                            message
                                .create_embed(|embed| {
                                    embed
                                        .author(|author| {
                                            author.name("List of tags").icon_url(AVATAR_URL)
                                        })
                                        .description(tags_str)
                                        .colour(EMBED_COLOR)
                                })
                                .flags(InteractionApplicationCommandCallbackDataFlags::EPHEMERAL)
                        })
                })
                .await
                .map_err(|err| {
                    error!("failed to reply to interaction {}", err);
                    err
                })?;
        }

        Ok(())
    }
}
