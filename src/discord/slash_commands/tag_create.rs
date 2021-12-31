use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::database::settings::Setting;
use crate::discord::slash_commands::tag_create::TagCreateCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, is_staff, verify_guild_slash_command,
    ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
};
use crate::service::setting::SettingService;
use crate::service::tag::{InsertTagFailure, TagService};
use anyhow::bail;
use serenity::model::Permissions;

pub struct TagCreateCommand;

struct TagCreateCommandOptions<'a> {
    name: &'a str,
    content: &'a str,
}

enum TagCreateCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
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

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("tag-create")
            .description("registers a message that can be repeated later")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("name")
                    .description("tag name to create")
                    .kind(ApplicationCommandOptionType::String)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("content")
                    .description("tag content")
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
            // TODO(sam): use unauthorized reply function
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You don't have enough permissions to execute this command!",
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
            Ok(_) => invisible_success_reply(&context.http, interaction, "Success.").await,
            Err(InsertTagFailure::ContentTooBig) => {
                invisible_failure_reply(&context.http, interaction, "Given content is too long!")
                    .await
            }
            Err(InsertTagFailure::TagExists) => {
                invisible_failure_reply(&context.http, interaction, "Tag already exists!").await
            }
            Err(InsertTagFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Failed to create tag, this incident was logged.",
                )
                .await
            }
        }

        Ok(())
    }
}
