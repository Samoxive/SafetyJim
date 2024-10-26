use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::model::id::RoleId;
use serenity::model::Permissions;

use crate::config::Config;
use crate::discord::slash_commands::role_create::RoleCreateCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction,
};
use crate::service::iam_role::{IAMRoleService, InsertIAMRoleFailure};
use crate::service::Services;

pub struct RoleCreateCommand;

struct RoleCreateCommandOptions {
    role_id: RoleId,
}

enum RoleCreateCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<RoleCreateCommandOptions, RoleCreateCommandOptionFailure> {
    let role = if let Some(role) = data.role("role") {
        role
    } else {
        return Err(MissingOption);
    };

    Ok(RoleCreateCommandOptions { role_id: role.id })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator()
}

#[async_trait]
impl SlashCommand for RoleCreateCommand {
    fn command_name(&self) -> &'static str {
        "role-create"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("role-create")
            .kind(CommandType::ChatInput)
            .description("registers a role that can be self assigned via iam command")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .default_member_permissions(Permissions::ADMINISTRATOR)
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::Role,
                    "role",
                    "self assignable role to register",
                )
                .required(true),
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

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        if !is_authorized(permissions) {
            unauthorized_reply(&context.http, interaction, Permissions::ADMINISTRATOR).await;
            return Ok(());
        }

        let iam_role_service = if let Some(service) = services.get::<IAMRoleService>() {
            service
        } else {
            bail!("couldn't get role service!");
        };

        match iam_role_service
            .insert_iam_role(guild_id, options.role_id)
            .await
        {
            Ok(_) => reply_to_interaction_str(&context.http, interaction, "Success.", true).await,
            Err(InsertIAMRoleFailure::RoleExists) => {
                reply_to_interaction_str(&context.http, interaction, "Role already exists!", true)
                    .await;
            }
            Err(InsertIAMRoleFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Failed to create role, this incident was logged.",
                    true,
                )
                .await
            }
        }

        Ok(())
    }
}
