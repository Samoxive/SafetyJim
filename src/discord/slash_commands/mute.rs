use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::mute::MuteCommandOptionFailure::{
    DurationParseError, MissingOption,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
    UserExt,
};
use crate::service::mute::{MuteFailure, MuteService};
use crate::service::setting::SettingService;
use anyhow::bail;
use serenity::model::user::User;
use serenity::model::Permissions;
use std::time::Duration;

pub struct MuteCommand;

struct MuteCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
    duration: Option<Duration>,
}

enum MuteCommandOptionFailure<'a> {
    MissingOption,
    DurationParseError(&'a str),
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<MuteCommandOptions, MuteCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    let reason = data.string("reason").map(String::from);

    let duration = if let Some(s) = data.string("duration") {
        if let Ok(duration) = humantime::parse_duration(s) {
            Some(duration)
        } else {
            return Err(DurationParseError(s));
        }
    } else {
        None
    };

    Ok(MuteCommandOptions {
        target_user,
        reason,
        duration,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.manage_roles()
}

#[async_trait]
impl SlashCommand for MuteCommand {
    fn command_name(&self) -> &'static str {
        "mute"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("mute")
            .description("mutes given user, time can be given for a temporary mute")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("user")
                    .description("target user to mute")
                    .kind(ApplicationCommandOptionType::User)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("reason")
                    .description("reason for the mute")
                    .kind(ApplicationCommandOptionType::String)
                    .required(false)
            })
            .create_option(|option| {
                option
                    .name("duration")
                    .description("duration for the mute")
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
            unauthorized_reply(&*context.http, interaction, Permissions::MANAGE_ROLES).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(DurationParseError(duration)) => {
                invisible_failure_reply(
                    &*context.http,
                    interaction,
                    &format!("Failed to understand duration: {}", duration),
                )
                .await;
                return Ok(());
            }
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let mute_service = if let Some(service) = services.get::<MuteService>() {
            service
        } else {
            bail!("couldn't get mute service!");
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        let guild = context.http.get_guild(guild_id.0).await?;

        match mute_service
            .issue_mute(
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
                options.duration,
                0,
            )
            .await
        {
            Ok(_) => {
                invisible_success_reply(&context.http, interaction, "Success.").await;
            }
            Err(MuteFailure::Unauthorized) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                )
                .await;
            }
            Err(MuteFailure::UnauthorizedFetchRoles) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to find the Muted role!",
                )
                .await;
            }
            Err(MuteFailure::UnauthorizedCreateRole) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Couldn't find the Muted role, creating it failed due to insufficient permissions!",
                )
                    .await;
            }
            Err(MuteFailure::UnauthorizedChannelOverride) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Couldn't find the Muted role, setting it up while creating the role failed due to insufficient permissions!",
                )
                    .await;
            }
            Err(MuteFailure::ModLogError(err)) => {
                invisible_failure_reply(&context.http, interaction, err.to_interaction_response())
                    .await;
            }
            Err(MuteFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Could not mute specified user for unknown reasons, this incident has been logged.",
                )
                    .await;
            }
        }

        Ok(())
    }
}
