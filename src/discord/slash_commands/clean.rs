use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{
    CreateApplicationCommand, CreateApplicationCommandOption, CreateInteractionResponse,
};
use serenity::client::Context;
use serenity::futures::StreamExt;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
};
use serenity::model::application::interaction::InteractionResponseType;
use serenity::model::id::MessageId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::clean::CleanCommandOptionFailure::{
    MissingOption, OutOfRangeError,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    clean_messages, edit_interaction_response, invisible_failure_reply, unauthorized_reply,
    verify_guild_slash_command, CleanMessagesFailure, CommandDataExt, GuildSlashCommandInteraction,
};

pub struct CleanCommand;

struct CleanCommandOptions {
    number: i64,
}

enum CleanCommandOptionFailure {
    MissingOption,
    OutOfRangeError(i64),
}

fn generate_options(data: &CommandData) -> Result<CleanCommandOptions, CleanCommandOptionFailure> {
    let number = if let Some(number) = data.integer("number") {
        if number > 0 && number <= 100 {
            number
        } else {
            return Err(OutOfRangeError(number));
        }
    } else {
        return Err(MissingOption);
    };

    Ok(CleanCommandOptions { number })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.manage_messages()
}

#[async_trait]
impl SlashCommand for CleanCommand {
    fn command_name(&self) -> &'static str {
        "clean"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::new("clean")
            .description("deletes specified number of messages")
            .dm_permission(false)
            .default_member_permissions(Permissions::MANAGE_MESSAGES)
            .add_option(
                CreateApplicationCommandOption::new(
                    CommandOptionType::Integer,
                    "number",
                    "number of messages to delete",
                )
                .required(true)
                .min_int_value(1)
                .max_int_value(100),
            )
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        _config: &Config,
        _services: &TypeMap,
    ) -> anyhow::Result<()> {
        let GuildSlashCommandInteraction {
            guild_id: _,
            member: _,
            permissions,
        } = verify_guild_slash_command(interaction)?;

        let channel_id = interaction.channel_id;

        if !is_authorized(permissions) {
            unauthorized_reply(&*context.http, interaction, Permissions::MANAGE_MESSAGES).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(OutOfRangeError(number)) => {
                invisible_failure_reply(
                    &*context.http,
                    interaction,
                    &format!("Number is out of range, must be between 1-100: {}", number),
                )
                .await;
                return Ok(());
            }
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let response = CreateInteractionResponse::default()
            .kind(InteractionResponseType::DeferredChannelMessageWithSource);

        interaction
            .create_interaction_response(&context.http, response)
            .await?;

        let message_ids_results: Vec<Result<MessageId, serenity::Error>> = channel_id
            .messages_iter(&context.http)
            .boxed()
            .skip(1)
            .take(options.number as usize)
            .map(|message| message.map(|message| message.id))
            .collect()
            .await;

        let (message_ids, errors): (Vec<_>, Vec<_>) =
            message_ids_results.into_iter().partition(Result::is_ok);

        let message_ids: Vec<MessageId> = message_ids.into_iter().map(Result::unwrap).collect();

        if !errors.is_empty() {
            edit_interaction_response(
                &context.http,
                interaction,
                "Failed to select messages to clean, make sure I have required permissions.",
            )
            .await;
            return Ok(());
        }

        let message_count = message_ids.len();
        let result = clean_messages(&context.http, channel_id, message_ids).await;
        let content = match result {
            Ok(_) => format!("Cleared {} message(s).", message_count),
            Err(CleanMessagesFailure::Unauthorized) => "I don't have enough permissions to do that! Required permission: Manage Messages, Read Message History".into(),
            Err(CleanMessagesFailure::Other) => "Failed to clean messages.".into()
        };
        edit_interaction_response(&context.http, interaction, &content).await;

        Ok(())
    }
}
