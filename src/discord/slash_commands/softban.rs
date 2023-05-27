use anyhow::bail;
use async_trait::async_trait;
use serenity::all::{CommandData, CommandInteraction, CommandOptionType, CommandType};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::client::Context;
use serenity::model::user::User;
use serenity::model::Permissions;

use crate::config::Config;
use crate::constants::JIM_ID;
use crate::discord::slash_commands::softban::SoftbanCommandOptionFailure::{
    DaysOutOfRange, MissingOption,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, UserExt,
};
use crate::service::guild::GuildService;
use crate::service::setting::SettingService;
use crate::service::softban::{SoftbanFailure, SoftbanService};
use crate::service::Services;

pub struct SoftbanCommand;

struct SoftbanCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
    days: Option<u8>,
}

enum SoftbanCommandOptionFailure {
    MissingOption,
    DaysOutOfRange(i64),
}

fn generate_options(
    data: &CommandData,
) -> Result<SoftbanCommandOptions, SoftbanCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    let reason = data.string("reason").map(String::from);

    let days = data.integer("days");

    if let Some(days) = days {
        if !(1..=7).contains(&days) {
            return Err(DaysOutOfRange(days));
        }
    }

    Ok(SoftbanCommandOptions {
        target_user,
        reason,
        days: days.map(|days| days as u8),
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.ban_members()
}

#[async_trait]
impl SlashCommand for SoftbanCommand {
    fn command_name(&self) -> &'static str {
        "softban"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("softban")
            .kind(CommandType::ChatInput)
            .description("kicks given user, deleting their last messages, defaults to a day")
            .dm_permission(false)
            .default_member_permissions(Permissions::BAN_MEMBERS)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to softban")
                    .required(true),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "reason",
                    "reason for the softban",
                )
                .required(false),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::Integer,
                    "days",
                    "number of days to delete last messages",
                )
                .required(false)
                .min_int_value(1)
                .max_int_value(7),
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
            Err(DaysOutOfRange(days)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    &format!(
                        "Given days: {} is out of range (must be between 1 and 7)",
                        days
                    ),
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
                "You can't softban yourself, dummy!",
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
                "You can't softban owner of the server!",
                true,
            )
            .await;
            return Ok(());
        }

        let softban_service = if let Some(service) = services.get::<SoftbanService>() {
            service
        } else {
            bail!("couldn't get softban service!");
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        match softban_service
            .issue_softban(
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
                options.days.unwrap_or(1),
                0,
            )
            .await
        {
            Ok(_) => {
                reply_to_interaction_str(&context.http, interaction, "Success.", true).await;
            }
            Err(SoftbanFailure::Unauthorized) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                    true,
                )
                .await;
            }
            Err(SoftbanFailure::ModLogError(err)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    err.to_interaction_response(),
                    true,
                )
                .await;
            }
            Err(SoftbanFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Could not softban specified user for unknown reasons, this incident has been logged.",
                    true,
                )
                    .await;
            }
        }

        Ok(())
    }
}
