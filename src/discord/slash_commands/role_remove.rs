use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::client::Context;
use serenity::model::application::command::{CommandOptionType, CommandType};
use serenity::model::application::interaction::application_command::{
    CommandData, CommandInteraction,
};
use serenity::model::id::RoleId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::role_remove::RoleRemoveCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, CommandDataExt, GuildSlashCommandInteraction,
};
use crate::service::iam_role::{IAMRoleService, RemoveIAMRoleFailure};

pub struct RoleRemoveCommand;

struct RoleRemoveCommandOptions {
    role_id: RoleId,
}

enum RoleRemoveCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<RoleRemoveCommandOptions, RoleRemoveCommandOptionFailure> {
    let role = if let Some(role) = data.role("role") {
        role
    } else {
        return Err(MissingOption);
    };

    Ok(RoleRemoveCommandOptions { role_id: role.id })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator()
}

#[async_trait]
impl SlashCommand for RoleRemoveCommand {
    fn command_name(&self) -> &'static str {
        "role-remove"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("role-remove")
            .kind(CommandType::ChatInput)
            .description("unregisters a self assignable role")
            .dm_permission(false)
            .default_member_permissions(Permissions::ADMINISTRATOR)
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::Role,
                    "role",
                    "self assignable role to unregister",
                )
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
            member: _,
            permissions,
        } = verify_guild_slash_command(interaction)?;

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        if !is_authorized(permissions) {
            unauthorized_reply(&*context.http, interaction, Permissions::ADMINISTRATOR).await;
            return Ok(());
        }

        let iam_role_service = if let Some(service) = services.get::<IAMRoleService>() {
            service
        } else {
            bail!("couldn't get role service!");
        };

        match iam_role_service
            .delete_iam_role(guild_id, options.role_id)
            .await
        {
            Ok(_) => invisible_success_reply(&context.http, interaction, "Success.").await,
            Err(RemoveIAMRoleFailure::RoleDoesNotExist) => {
                invisible_failure_reply(&context.http, interaction, "Role does not exist!").await
            }
            Err(RemoveIAMRoleFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Failed to remove role, this incident was logged.",
                )
                .await
            }
        }

        Ok(())
    }
}
