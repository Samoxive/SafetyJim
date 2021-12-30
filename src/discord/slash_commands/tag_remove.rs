use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::database::settings::Setting;
use crate::discord::slash_commands::tag_remove::TagRemoveCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, is_staff, verify_guild_slash_command,
    ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
};
use crate::service::setting::SettingService;
use crate::service::tag::{RemoveTagFailure, TagService};
use anyhow::bail;
use serenity::model::Permissions;

pub struct TagRemoveCommand;

struct TagRemoveCommandOptions<'a> {
    name: &'a str,
}

enum TagRemoveCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<TagRemoveCommandOptions, TagRemoveCommandOptionFailure> {
    let name = if let Some(s) = data.string("name") {
        s
    } else {
        return Err(MissingOption);
    };

    Ok(TagRemoveCommandOptions { name })
}

fn is_authorized(setting: &Setting, permissions: Permissions) -> bool {
    if setting.mods_can_edit_tags {
        is_staff(permissions)
    } else {
        permissions.administrator()
    }
}

#[async_trait]
impl SlashCommand for TagRemoveCommand {
    fn command_name(&self) -> &'static str {
        "tag-remove"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("tag-remove")
            .description("remove previously registered tag")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("name")
                    .description("tag name to remove")
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

        match tag_service.remove_tag(guild_id, options.name).await {
            Ok(_) => invisible_success_reply(&context.http, interaction, "Success.").await,
            Err(RemoveTagFailure::TagDoesNotExist) => {
                invisible_failure_reply(&context.http, interaction, "Tag does not exist!").await
            }
            Err(RemoveTagFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Failed to remove tag, this incident was logged.",
                )
                .await
            }
        }

        Ok(())
    }
}
