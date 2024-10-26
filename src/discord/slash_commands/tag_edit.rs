use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::model::Permissions;

use crate::config::Config;
use crate::database::settings::Setting;
use crate::discord::slash_commands::tag_edit::TagEditCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    is_staff, reply_to_interaction_str, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction,
};
use crate::service::setting::SettingService;
use crate::service::tag::{TagService, UpdateTagFailure};
use crate::service::Services;

pub struct TagEditCommand;

struct TagEditCommandOptions<'a> {
    name: &'a str,
    content: &'a str,
}

enum TagEditCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<TagEditCommandOptions, TagEditCommandOptionFailure> {
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

    Ok(TagEditCommandOptions { name, content })
}

fn is_authorized(setting: &Setting, permissions: Permissions) -> bool {
    if setting.mods_can_edit_tags {
        is_staff(permissions)
    } else {
        permissions.administrator()
    }
}

#[async_trait]
impl SlashCommand for TagEditCommand {
    fn command_name(&self) -> &'static str {
        "tag-edit"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("tag-edit")
            .kind(CommandType::ChatInput)
            .description("edits previously registered tag content")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .add_option(
                CreateCommandOption::new(CommandOptionType::String, "name", "tag name to edit")
                    .required(true)
                    .set_autocomplete(true),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "content",
                    "content to replace tag with",
                )
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
                "You don't have enough permissions to execute this command! ",
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
            .update_tag(guild_id, options.name, options.content)
            .await
        {
            Ok(_) => reply_to_interaction_str(&context.http, interaction, "Success.", true).await,
            Err(UpdateTagFailure::ContentTooBig) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Given content is too long!",
                    true,
                )
                .await
            }
            Err(UpdateTagFailure::TagDoesNotExist) => {
                reply_to_interaction_str(&context.http, interaction, "Tag does not exist!", true)
                    .await;
            }
            Err(UpdateTagFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Failed to edit the tag, this incident was logged.",
                    true,
                )
                .await
            }
        }

        Ok(())
    }
}
