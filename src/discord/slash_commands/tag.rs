use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{CreateApplicationCommand, CreateApplicationCommandOption};
use serenity::client::Context;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
};
use serenity::model::id::UserId;
use serenity::prelude::Mentionable;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::tag::TagCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, reply_with_str, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction,
};
use crate::service::tag::TagService;

pub struct TagCommand;

struct TagCommandOptions<'a> {
    name: &'a str,
    mention_user: Option<UserId>,
}

enum TagCommandOptionFailure {
    MissingOption,
}

fn generate_options(data: &CommandData) -> Result<TagCommandOptions, TagCommandOptionFailure> {
    let name = if let Some(s) = data.string("name") {
        s
    } else {
        return Err(MissingOption);
    };

    let mention_user = data.user("mention").map(|(user, _)| user.id);

    Ok(TagCommandOptions { name, mention_user })
}

#[async_trait]
impl SlashCommand for TagCommand {
    fn command_name(&self) -> &'static str {
        "tag"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::default()
            .name("tag")
            .description("repeats previously registered message via tag name")
            .dm_permission(false)
            .add_option(
                CreateApplicationCommandOption::default()
                    .name("name")
                    .description("tag name for message")
                    .kind(CommandOptionType::String)
                    .required(true)
                    .set_autocomplete(true),
            )
            .add_option(
                CreateApplicationCommandOption::default()
                    .name("mention")
                    .description("user to be mentioned alongside tag content")
                    .kind(CommandOptionType::User)
                    .required(false),
            )
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

        let mut content =
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

        if let Some(mention_user) = options.mention_user {
            content.push('\n');
            content.push_str(&mention_user.mention().to_string())
        }

        reply_with_str(&context.http, interaction, &content).await;
        Ok(())
    }
}
