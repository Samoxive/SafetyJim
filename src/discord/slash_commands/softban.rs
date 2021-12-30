use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::softban::SoftbanCommandOptionFailure::{
    DaysOutOfRange, MissingOption,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
    UserExt,
};
use crate::service::setting::SettingService;
use crate::service::softban::{SoftbanFailure, SoftbanService};
use anyhow::bail;
use serenity::model::user::User;
use serenity::model::Permissions;

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
    data: &ApplicationCommandInteractionData,
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

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("softban")
            .description("kicks given user, deleting their last messages, defaults to a day")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("user")
                    .description("target user to softban")
                    .kind(ApplicationCommandOptionType::User)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("reason")
                    .description("reason for the softban")
                    .kind(ApplicationCommandOptionType::String)
                    .required(false)
            })
            .create_option(|option| {
                option
                    .name("days")
                    .description("number of days to delete last messages")
                    .kind(ApplicationCommandOptionType::Integer)
                    .required(false)
                    .min_int_value(1)
                    .max_int_value(7)
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
            Err(DaysOutOfRange(days)) => {
                invisible_failure_reply(
                    &*context.http,
                    interaction,
                    &format!(
                        "Given days: {} is out of range (must be between 1 and 7)",
                        days
                    ),
                )
                .await;
                return Ok(());
            }
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

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

        let guild = context.http.get_guild(guild_id.0).await?;

        match softban_service
            .issue_softban(
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
                options.days.unwrap_or(1),
                0,
            )
            .await
        {
            Ok(_) => {
                invisible_success_reply(&context.http, interaction, "Success.").await;
            }
            Err(SoftbanFailure::Unauthorized) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                )
                .await;
            }
            Err(SoftbanFailure::ModLogError(err)) => {
                invisible_failure_reply(&context.http, interaction, err.to_interaction_response())
                    .await;
            }
            Err(SoftbanFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Could not softban specified user for unknown reasons, this incident has been logged.",
                )
                    .await;
            }
        }

        Ok(())
    }
}
