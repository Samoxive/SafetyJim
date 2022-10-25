use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::client::Context;
use serenity::model::application::command::{CommandOptionType, CommandType};
use serenity::model::application::interaction::application_command::{
    CommandData, CommandInteraction,
};
use serenity::model::id::RoleId;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, SerenityErrorExt,
};
use crate::service::iam_role::IAMRoleService;

pub struct IAMNotCommand;

struct IAMNotCommandOptions {
    role_id: RoleId,
}

enum IAMNotCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<IAMNotCommandOptions, IAMNotCommandOptionFailure> {
    let role = if let Some(role) = data.role("role") {
        role
    } else {
        return Err(IAMNotCommandOptionFailure::MissingOption);
    };

    Ok(IAMNotCommandOptions { role_id: role.id })
}

#[async_trait]
impl SlashCommand for IAMNotCommand {
    fn command_name(&self) -> &'static str {
        "iam-not"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("iam-not")
            .kind(CommandType::ChatInput)
            .description("removes specified self assigned role")
            .dm_permission(false)
            .add_option(
                CreateCommandOption::new(CommandOptionType::Role, "role", "role to remove")
                    .required(true),
            )
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &CommandInteraction,
        _config: &Config,
        services: &TypeMap,
    ) -> anyhow::Result<()> {
        let GuildSlashCommandInteraction {
            guild_id,
            member,
            permissions: _,
        } = verify_guild_slash_command(interaction)?;

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(IAMNotCommandOptionFailure::MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let iam_role_service = if let Some(service) = services.get::<IAMRoleService>() {
            service
        } else {
            bail!("couldn't get iam role service!");
        };

        if !iam_role_service
            .is_iam_role(guild_id, options.role_id)
            .await
        {
            invisible_failure_reply(
                &context.http,
                interaction,
                "Could not find a role with specified name!",
            )
            .await;
            return Ok(());
        }

        if let Err(err) = context
            .http
            .remove_member_role(
                guild_id,
                member.user.id,
                options.role_id,
                Some("Member self-unassigned the role"),
            )
            .await
        {
            if let Some(error_code) = err.discord_error_code() {
                let error_message = match error_code {
                    50013 => "Could not remove specified role. Do I have enough permissions?",
                    10011 => "This role no longer exists!",
                    _ => bail!("failed to issue discord member role remove {}", err),
                };

                invisible_failure_reply(&context.http, interaction, error_message).await;
            }
        } else {
            invisible_success_reply(&context.http, interaction, "Removed!").await;
        }

        Ok(())
    }
}
