use std::time::Duration;

use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{CreateApplicationCommand, CreateApplicationCommandOption};
use serenity::client::Context;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
};
use serenity::model::user::User;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::constants::JIM_ID;
use crate::discord::slash_commands::mute::MuteCommandOptionFailure::{
    DurationParseError, MissingOption,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, CommandDataExt, GuildSlashCommandInteraction, UserExt,
};
use crate::service::guild::GuildService;
use crate::service::mute::{MuteFailure, MuteService};
use crate::service::setting::SettingService;

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

fn generate_options(data: &CommandData) -> Result<MuteCommandOptions, MuteCommandOptionFailure> {
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
    permissions.administrator() || permissions.manage_roles()
}

#[async_trait]
impl SlashCommand for MuteCommand {
    fn command_name(&self) -> &'static str {
        "mute"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::new("mute")
            .description("mutes given user, time can be given for a temporary mute")
            .dm_permission(false)
            .default_member_permissions(Permissions::MANAGE_ROLES)
            .add_option(
                CreateApplicationCommandOption::new(
                    CommandOptionType::User,
                    "user",
                    "target user to mute",
                )
                .required(true),
            )
            .add_option(
                CreateApplicationCommandOption::new(
                    CommandOptionType::String,
                    "reason",
                    "reason for the mute",
                )
                .required(false),
            )
            .add_option(
                CreateApplicationCommandOption::new(
                    CommandOptionType::String,
                    "duration",
                    "duration for the mute",
                )
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

        if options.target_user.id == mod_user.id {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You can't mute yourself, dummy!",
            )
            .await;
            return Ok(());
        }

        if options.target_user.id == JIM_ID {
            invisible_failure_reply(&*context.http, interaction, "Now that's just rude.").await;
            return Ok(());
        }

        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            bail!("couldn't get guild service!");
        };

        let guild = guild_service.get_guild(guild_id).await?;

        if options.target_user.id == guild.owner_id {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You can't mute owner of the server!",
            )
            .await;
            return Ok(());
        }

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

        match mute_service
            .issue_mute(
                &context.http,
                guild_id,
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
