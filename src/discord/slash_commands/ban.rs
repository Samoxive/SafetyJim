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
use crate::discord::slash_commands::ban::BanCommandOptionFailure::{
    DurationParseError, MissingOption,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, UserExt,
};
use crate::service::ban::{BanFailure, BanService};
use crate::service::guild::GuildService;
use crate::service::setting::SettingService;
use crate::service::Services;

pub struct BanCommand;

struct BanCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
    duration: Option<Duration>,
}

enum BanCommandOptionFailure<'a> {
    MissingOption,
    DurationParseError(&'a str),
}

fn generate_options(data: &CommandData) -> Result<BanCommandOptions, BanCommandOptionFailure> {
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

    Ok(BanCommandOptions {
        target_user,
        reason,
        duration,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.ban_members()
}

#[async_trait]
impl SlashCommand for BanCommand {
    fn command_name(&self) -> &'static str {
        "ban"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("ban")
            .kind(CommandType::ChatInput)
            .description("bans given user, time can be given for a temporary ban")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .default_member_permissions(Permissions::BAN_MEMBERS)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to ban")
                    .required(true),
            )
            .add_option(
                CreateCommandOption::new(CommandOptionType::String, "reason", "reason for the ban")
                    .required(false),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "duration",
                    "duration for the ban",
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
            unauthorized_reply(&context.http, interaction, Permissions::BAN_MEMBERS).await;
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
                "You can't ban yourself, dummy!",
                true,
            )
            .await;
            return Ok(());
        }

        if options.target_user.id == JIM_ID {
            // Sanity check: Jim cannot ban itself.
            // c.f. https://discord.com/channels/238666723824238602/238666723824238602/902275716299776090
            reply_to_interaction_str(
                &context.http,
                interaction,
                "I'm sorry, Dave. I'm afraid I can't do that.",
                true,
            )
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
                "You can't ban owner of the server!",
                true,
            )
            .await;
            return Ok(());
        }

        let ban_service = if let Some(service) = services.get::<BanService>() {
            service
        } else {
            bail!("couldn't get ban service!");
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        match ban_service
            .issue_ban(
                &context.http,
                guild_id,
                &guild.name,
                &setting,
                Some(channel_id),
                mod_user.id,
                &mod_user.tag_and_id(),
                options.target_user,
                options
                    .reason
                    .unwrap_or_else(|| "No reason specified".into()),
                options.duration,
            )
            .await
        {
            Ok(_) => {
                let _ =
                    reply_to_interaction_str(&context.http, interaction, "Success.", true).await;
            }
            Err(BanFailure::Unauthorized) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                    true,
                )
                .await;
            }
            Err(BanFailure::ModLogError(err)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    err.to_interaction_response(),
                    true,
                )
                .await;
            }
            Err(BanFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Could not ban specified user for unknown reasons, this incident has been logged.",
                    true,
                )
                    .await;
            }
        }

        Ok(())
    }
}
