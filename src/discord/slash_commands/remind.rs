use std::time::Duration;

use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
};
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::remind::RemindCommandFailure::{
    DurationParseError, MissingOption,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction,
};
use crate::service::reminder::ReminderService;

pub struct RemindCommand;

struct RemindCommandOptions {
    message: String,
    duration: Option<Duration>,
}

enum RemindCommandFailure<'a> {
    MissingOption,
    DurationParseError(&'a str),
}

fn generate_options(data: &CommandData) -> Result<RemindCommandOptions, RemindCommandFailure> {
    let message = if let Some(message) = data.string("message").map(String::from) {
        message
    } else {
        return Err(MissingOption);
    };

    let duration = if let Some(s) = data.string("duration") {
        if let Ok(duration) = humantime::parse_duration(s) {
            Some(duration)
        } else {
            return Err(DurationParseError(s));
        }
    } else {
        None
    };

    Ok(RemindCommandOptions { message, duration })
}

#[async_trait]
impl SlashCommand for RemindCommand {
    fn command_name(&self) -> &'static str {
        "remind"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("remind")
            .description("sets a reminder for a future date, duration defaults to a day")
            .dm_permission(false)
            .create_option(|option| {
                option
                    .name("message")
                    .description("message to be reminded of")
                    .kind(CommandOptionType::String)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("duration")
                    .description("duration after which notification is sent")
                    .kind(CommandOptionType::String)
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
            permissions: _,
        } = verify_guild_slash_command(interaction)?;

        let channel_id = interaction.channel_id;
        let user = &interaction.user;

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

        let reminder_service = if let Some(service) = services.get::<ReminderService>() {
            service
        } else {
            bail!("couldn't get reminder service!");
        };

        match reminder_service
            .create_reminder(
                guild_id,
                channel_id,
                user,
                options.duration,
                options.message,
            )
            .await
        {
            Ok(_) => invisible_success_reply(&context.http, interaction, "Success.").await,
            Err(_) => invisible_failure_reply(
                &context.http,
                interaction,
                "Could not save the reminder for unknown reasons, this incident has been logged.",
            )
            .await,
        }

        Ok(())
    }
}
