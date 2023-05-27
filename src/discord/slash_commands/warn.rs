use anyhow::bail;
use async_trait::async_trait;
use serenity::all::{CommandData, CommandInteraction, CommandOptionType, CommandType};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::client::Context;
use serenity::model::user::User;
use serenity::model::Permissions;

use crate::config::Config;
use crate::constants::JIM_ID;
use crate::discord::slash_commands::warn::WarnCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, UserExt,
};
use crate::service::guild::GuildService;
use crate::service::setting::SettingService;
use crate::service::warn::{WarnFailure, WarnService};
use crate::service::Services;

pub struct WarnCommand;

struct WarnCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
}

enum WarnCommandOptionFailure {
    MissingOption,
}

fn generate_options(data: &CommandData) -> Result<WarnCommandOptions, WarnCommandOptionFailure> {
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

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("warn")
            .kind(CommandType::ChatInput)
            .description("warns given user")
            .dm_permission(false)
            .default_member_permissions(Permissions::KICK_MEMBERS)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to warn")
                    .required(true),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "reason",
                    "reason for the warning",
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
            unauthorized_reply(&context.http, interaction, Permissions::KICK_MEMBERS).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        if options.target_user.id == mod_user.id {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "You can't warn yourself, dummy!",
                true,
            )
            .await;
            return Ok(());
        }

        if options.target_user.id == JIM_ID {
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
                "You can't warn owner of the server!",
                true,
            )
            .await;
            return Ok(());
        }

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

        match warn_service
            .issue_warn(
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
                0,
            )
            .await
        {
            Ok(_) => {
                reply_to_interaction_str(&context.http, interaction, "Success.", true).await;
            }
            Err(WarnFailure::ModLogError(err)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    err.to_interaction_response(),
                    true,
                )
                .await;
            }
        }

        Ok(())
    }
}
