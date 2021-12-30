use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::hardban::HardbanCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
    UserExt,
};
use crate::service::hardban::{HardbanFailure, HardbanService};
use crate::service::setting::SettingService;
use anyhow::bail;
use serenity::model::user::User;
use serenity::model::Permissions;

pub struct HardbanCommand;

struct HardbanCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
}

enum HardbanCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<HardbanCommandOptions, HardbanCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    let reason = data.string("reason").map(String::from);

    Ok(HardbanCommandOptions {
        target_user,
        reason,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.ban_members()
}

#[async_trait]
impl SlashCommand for HardbanCommand {
    fn command_name(&self) -> &'static str {
        "hardban"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("hardban")
            .description("hardbans given user, deleting all messages in last 7 days")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("user")
                    .description("target user to hardban")
                    .kind(ApplicationCommandOptionType::User)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("reason")
                    .description("reason for the hardban")
                    .kind(ApplicationCommandOptionType::String)
                    .required(false)
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

        let channel_id = interaction.channel_id;
        let mod_user = &interaction.user;

        if !is_authorized(permissions) {
            unauthorized_reply(&*context.http, interaction, Permissions::BAN_MEMBERS).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let hardban_service = if let Some(service) = services.get::<HardbanService>() {
            service
        } else {
            bail!("couldn't get hardban service!");
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        let guild = context.http.get_guild(guild_id.0).await?;

        match hardban_service
            .issue_hardban(
                &context.http,
                guild.id,
                &guild.name,
                &setting,
                Some(channel_id),
                mod_user.id,
                &mod_user.tag_and_id(),
                options.target_user,
                options
                    .reason
                    .unwrap_or_else(|| "No reason specified".into()),
            )
            .await
        {
            Ok(_) => {
                invisible_success_reply(&context.http, interaction, "Success.").await;
            }
            Err(HardbanFailure::Unauthorized) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                )
                .await;
            }
            Err(HardbanFailure::ModLogError(err)) => {
                invisible_failure_reply(&context.http, interaction, err.to_interaction_response())
                    .await;
            }
            Err(HardbanFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Could not hardban specified user for unknown reasons, this incident has been logged.",
                )
                    .await;
            }
        }

        Ok(())
    }
}
