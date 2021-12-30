use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use anyhow::bail;
use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::warn::WarnCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
    UserExt,
};
use crate::service::setting::SettingService;
use crate::service::warn::{WarnFailure, WarnService};
use serenity::model::user::User;
use serenity::model::Permissions;

pub struct WarnCommand;

struct WarnCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
}

enum WarnCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<WarnCommandOptions, WarnCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    let reason = data.string("reason").map(String::from);

    Ok(WarnCommandOptions {
        target_user,
        reason,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.kick_members()
}

#[async_trait]
impl SlashCommand for WarnCommand {
    fn command_name(&self) -> &'static str {
        "warn"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("warn")
            .description("warns given user")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("user")
                    .description("target user to warn")
                    .kind(ApplicationCommandOptionType::User)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("reason")
                    .description("reason for the warning")
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
            unauthorized_reply(&*context.http, interaction, Permissions::KICK_MEMBERS).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let warn_service = if let Some(service) = services.get::<WarnService>() {
            service
        } else {
            bail!("couldn't get warn service!");
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        let guild = context.http.get_guild(guild_id.0).await?;

        match warn_service
            .issue_warn(
                &context.http,
                guild.id,
                &guild.name,
                &setting,
                services,
                Some(channel_id),
                mod_user.id,
                &mod_user.tag_and_id(),
                options.target_user,
                options
                    .reason
                    .unwrap_or_else(|| "No reason specified".into()),
                0,
            )
            .await
        {
            Ok(_) => {
                invisible_success_reply(&context.http, interaction, "Success.").await;
            }
            Err(WarnFailure::ModLogError(err)) => {
                invisible_failure_reply(&context.http, interaction, err.to_interaction_response())
                    .await;
            }
        }

        Ok(())
    }
}
