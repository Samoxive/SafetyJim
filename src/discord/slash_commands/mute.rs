use std::time::Duration;

use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::model::user::User;
use serenity::model::Permissions;

use crate::config::Config;
use crate::constants::JIM_ID;
use crate::discord::slash_commands::mute::MuteCommandOptionFailure::{
    DurationParseError, MissingOption,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, UserExt,
};
use crate::service::guild::GuildService;
use crate::service::mute::{MuteFailure, MuteService};
use crate::service::setting::SettingService;
use crate::service::Services;

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

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("mute")
            .kind(CommandType::ChatInput)
            .description("mutes given user, time can be given for a temporary mute")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .default_member_permissions(Permissions::MANAGE_ROLES)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to mute")
                    .required(true),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "reason",
                    "reason for the mute",
                )
                .required(false),
            )
            .add_option(
                CreateCommandOption::new(
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
        interaction: &CommandInteraction,
        _config: &Config,
        services: &Services,
    ) -> anyhow::Result<()> {
        let GuildSlashCommandInteraction {
            guild_id,
            member: _,
            permissions,
        } = verify_guild_slash_command(interaction)?;

        let channel_id = interaction.channel_id;
        let mod_user = &interaction.user;

        if !is_authorized(permissions) {
            unauthorized_reply(&context.http, interaction, Permissions::MANAGE_ROLES).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(DurationParseError(duration)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    &format!("Failed to understand duration: {}", duration),
                    true,
                )
                .await;
                return Ok(());
            }
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        if options.target_user.id == mod_user.id {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "You can't mute yourself, dummy!",
                true,
            )
            .await;
            return Ok(());
        }

        if options.target_user.id == JIM_ID {
            reply_to_interaction_str(&context.http, interaction, "Now that's just rude.", true)
                .await;
            return Ok(());
        }

        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            bail!("couldn't get guild service!");
        };

        let guild = guild_service.get_guild(guild_id).await?;

        if options.target_user.id == guild.owner_id {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "You can't mute owner of the server!",
                true,
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
                reply_to_interaction_str(&context.http, interaction, "Success.", true).await;
            }
            Err(MuteFailure::Unauthorized) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                    true,
                )
                .await;
            }
            Err(MuteFailure::UnauthorizedFetchRoles) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to find the Muted role!",
                    true,
                )
                .await;
            }
            Err(MuteFailure::UnauthorizedCreateRole) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Couldn't find the Muted role, creating it failed due to insufficient permissions!",
                    true,
                )
                    .await;
            }
            Err(MuteFailure::UnauthorizedChannelOverride) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Couldn't find the Muted role, setting it up while creating the role failed due to insufficient permissions!",
                    true,
                )
                    .await;
            }
            Err(MuteFailure::ModLogError(err)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    err.to_interaction_response(),
                    true,
                )
                .await;
            }
            Err(MuteFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Could not mute specified user for unknown reasons, this incident has been logged.",
                    true,
                )
                    .await;
            }
        }

        Ok(())
    }
}
