use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::client::Context;
use serenity::model::application::command::{CommandOptionType, CommandType};
use serenity::model::application::interaction::application_command::{
    CommandData, CommandInteraction,
};
use serenity::model::Permissions;

use crate::config::Config;
use crate::database::settings::Setting;
use crate::discord::slash_commands::tag_create::TagCreateCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    is_staff, reply_to_interaction_str, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction,
};
use crate::service::setting::SettingService;
use crate::service::tag::{InsertTagFailure, TagService};
use crate::service::Services;

pub struct TagCreateCommand;

struct TagCreateCommandOptions<'a> {
    name: &'a str,
    content: &'a str,
}

enum TagCreateCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<TagCreateCommandOptions, TagCreateCommandOptionFailure> {
    let name = if let Some(s) = data.string("name") {
        s
    } else {
        return Err(MissingOption);
    };

    let content = if let Some(s) = data.string("content") {
        s
    } else {
        return Err(MissingOption);
    };

    Ok(TagCreateCommandOptions { name, content })
}

fn is_authorized(setting: &Setting, permissions: Permissions) -> bool {
    if setting.mods_can_edit_tags {
        is_staff(permissions)
    } else {
        permissions.administrator()
    }
}

#[async_trait]
impl SlashCommand for TagCreateCommand {
    fn command_name(&self) -> &'static str {
        "tag-create"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("tag-create")
            .kind(CommandType::ChatInput)
            .description("registers a message that can be repeated later")
            .dm_permission(false)
            .add_option(
                CreateCommandOption::new(CommandOptionType::String, "name", "tag name to create")
                    .required(true),
            )
            .add_option(
                CreateCommandOption::new(CommandOptionType::String, "content", "tag content")
                    .required(true),
            )
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
            permissions,
        } = verify_guild_slash_command(interaction)?;

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        // refer to tag-remove command permission check
        if !is_authorized(&setting, permissions) {
            // TODO(sam): use unauthorized reply function
            reply_to_interaction_str(
                &context.http,
                interaction,
                "You don't have enough permissions to execute this command!",
                true,
            )
            .await;
            return Ok(());
        }

        let tag_service = if let Some(service) = services.get::<TagService>() {
            service
        } else {
            bail!("couldn't get tag service!");
        };

        match tag_service
            .insert_tag(guild_id, options.name, options.content)
            .await
        {
            Ok(_) => reply_to_interaction_str(&context.http, interaction, "Success.", true).await,
            Err(InsertTagFailure::ContentTooBig) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Given content is too long!",
                    true,
                )
                .await
            }
            Err(InsertTagFailure::TagExists) => {
                reply_to_interaction_str(&context.http, interaction, "Tag already exists!", true)
                    .await;
            }
            Err(InsertTagFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Failed to create tag, this incident was logged.",
                    true,
                )
                .await
            }
        }

        Ok(())
    }
}
