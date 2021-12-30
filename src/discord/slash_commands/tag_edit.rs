use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::database::settings::Setting;
use crate::discord::slash_commands::tag_edit::TagEditCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, is_staff, verify_guild_slash_command,
    ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
};
use crate::service::setting::SettingService;
use crate::service::tag::{TagService, UpdateTagFailure};
use anyhow::bail;
use serenity::model::Permissions;

pub struct TagEditCommand;

struct TagEditCommandOptions<'a> {
    name: &'a str,
    content: &'a str,
}

enum TagEditCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
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

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("tag-edit")
            .description("edits previously registered tag content")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("name")
                    .description("tag name to edit")
                    .kind(ApplicationCommandOptionType::String)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("content")
                    .description("content to replace tag with")
                    .kind(ApplicationCommandOptionType::String)
                    .required(true)
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

        if !is_authorized(&setting, permissions) {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You don't have enough permissions to execute this command! ",
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
            Ok(_) => invisible_success_reply(&context.http, interaction, "Success.").await,
            Err(UpdateTagFailure::ContentTooBig) => {
                invisible_failure_reply(&context.http, interaction, "Given content is too long!")
                    .await
            }
            Err(UpdateTagFailure::TagDoesNotExist) => {
                invisible_failure_reply(&context.http, interaction, "Tag does not exist!").await
            }
            Err(UpdateTagFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Failed to edit the tag, this incident was logged.",
                )
                .await
            }
        }

        Ok(())
    }
}
