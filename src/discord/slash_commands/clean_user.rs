use std::future::ready;

use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::futures::StreamExt;
use serenity::model::id::{MessageId, UserId};
use serenity::model::Permissions;

use crate::config::Config;
use crate::discord::slash_commands::clean_user::CleanUserCommandOptionFailure::{
    MissingOption, OutOfRangeError,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    clean_messages, defer_interaction, edit_deferred_interaction_response,
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CleanMessagesFailure,
    CommandDataExt, GuildSlashCommandInteraction,
};
use crate::service::Services;

pub struct CleanUserCommand;

struct CleanUserCommandOptions {
    number: i64,
    user_id: UserId,
}

enum CleanUserCommandOptionFailure {
    MissingOption,
    OutOfRangeError(i64),
}

fn generate_options(
    data: &CommandData,
) -> Result<CleanUserCommandOptions, CleanUserCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    let number = if let Some(number) = data.integer("number") {
        if number > 0 && number <= 100 {
            number
        } else {
            return Err(OutOfRangeError(number));
        }
    } else {
        return Err(MissingOption);
    };

    Ok(CleanUserCommandOptions {
        user_id: target_user.id,
        number,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.manage_messages()
}

#[async_trait]
impl SlashCommand for CleanUserCommand {
    fn command_name(&self) -> &'static str {
        "clean-user"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("clean-user")
            .kind(CommandType::ChatInput)
            .description("deletes specified number of bot messages")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .default_member_permissions(Permissions::MANAGE_MESSAGES)
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::Integer,
                    "number",
                    "number of messages to delete",
                )
                .required(true)
                .min_int_value(1)
                .max_int_value(100),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::User,
                    "user",
                    "target user to clean messages from",
                )
                .required(true),
            )
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        _config: &Config,
        _services: &Services,
    ) -> anyhow::Result<()> {
        let GuildSlashCommandInteraction {
            guild_id: _,
            member,
            permissions,
        } = verify_guild_slash_command(interaction)?;

        let channel_id = interaction.channel_id;

        if !is_authorized(permissions) {
            unauthorized_reply(&context.http, interaction, Permissions::MANAGE_MESSAGES).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(OutOfRangeError(number)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    &format!("Number is out of range, must be between 1-100: {}", number),
                    true,
                )
                .await;
                return Ok(());
            }
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        defer_interaction(&context.http, interaction).await?;

        let message_ids_results: Vec<Result<MessageId, serenity::Error>> = channel_id
            .messages_iter(&context.http)
            .boxed()
            .skip(1)
            .take_while(|message| ready(message.is_ok()))
            .take(300)
            .filter(|message| {
                ready(
                    message
                        .as_ref()
                        .map(|message| message.author.id == options.user_id)
                        .unwrap_or(false),
                )
            })
            .take(options.number as usize)
            .map(|message| message.map(|message| message.id))
            .collect()
            .await;

        let (message_ids, errors): (Vec<_>, Vec<_>) =
            message_ids_results.into_iter().partition(Result::is_ok);

        let message_ids: Vec<MessageId> = message_ids.into_iter().map(Result::unwrap).collect();

        if !errors.is_empty() {
            edit_deferred_interaction_response(
                &context.http,
                interaction,
                "Failed to select messages to clean, make sure I have required permissions.",
            )
            .await;
            return Ok(());
        }

        let message_count = message_ids.len();
        let result = clean_messages(&context.http, channel_id, message_ids, &member.user).await;
        let content = match result {
            Ok(_) => format!("Cleared {} message(s).", message_count),
            Err(CleanMessagesFailure::Unauthorized) => "I don't have enough permissions to do that! Required permission: Manage Messages, Read Message History".into(),
            Err(CleanMessagesFailure::Other) => "Failed to clean messages.".into()
        };
        edit_deferred_interaction_response(&context.http, interaction, &content).await;

        Ok(())
    }
}
