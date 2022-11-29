use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{CreateCommand, CreateEmbed, CreateEmbedAuthor};
use serenity::client::Context;
use serenity::model::application::command::CommandType;
use serenity::model::application::interaction::application_command::CommandInteraction;

use crate::config::Config;
use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_embed, reply_to_interaction_str, verify_guild_slash_command,
    GuildSlashCommandInteraction,
};
use crate::service::tag::TagService;
use crate::service::Services;

pub struct TagListCommand;

#[async_trait]
impl SlashCommand for TagListCommand {
    fn command_name(&self) -> &'static str {
        "tag-list"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("tag-list")
            .kind(CommandType::ChatInput)
            .description("lists previously registered tags")
            .dm_permission(false)
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        _config: &Config,
        services: &Services,
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
            reply_to_interaction_str(
                &context.http,
                interaction,
                "No tags have been added yet!",
                true,
            )
            .await;
        } else {
            let tags_str = tags
                .iter()
                .map(|tag| format!("\u{2022} {}", tag))
                .collect::<Vec<String>>()
                .join("\n");

            let embed = CreateEmbed::default()
                .author(CreateEmbedAuthor::new("List of tags").icon_url(AVATAR_URL))
                .description(tags_str)
                .colour(EMBED_COLOR);

            reply_to_interaction_embed(&context.http, interaction, embed, true).await;
        }

        Ok(())
    }
}
