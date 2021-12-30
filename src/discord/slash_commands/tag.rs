use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::tag::TagCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, reply_with_str, verify_guild_slash_command,
    ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
};
use crate::service::tag::TagService;
use anyhow::bail;

pub struct TagCommand;

struct TagCommandOptions<'a> {
    name: &'a str,
}

enum TagCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<TagCommandOptions, TagCommandOptionFailure> {
    let name = if let Some(s) = data.string("name") {
        s
    } else {
        return Err(MissingOption);
    };

    Ok(TagCommandOptions { name })
}

#[async_trait]
impl SlashCommand for TagCommand {
    fn command_name(&self) -> &'static str {
        "tag"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("tag")
            .description("repeats previously registered message via tag name")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("name")
                    .description("tag name for message")
                    .kind(ApplicationCommandOptionType::String)
                    .required(true)
                    .set_autocomplete(true)
            })
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

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let tag_service = if let Some(service) = services.get::<TagService>() {
            service
        } else {
            bail!("couldn't get tag service!");
        };

        let content =
            if let Some(content) = tag_service.get_tag_content(guild_id, options.name).await {
                content
            } else {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Could not find a tag with that name!",
                )
                .await;
                return Ok(());
            };

        reply_with_str(&context.http, interaction, &content).await;
        Ok(())
    }
}
