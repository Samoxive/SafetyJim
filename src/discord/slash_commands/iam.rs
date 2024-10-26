use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::model::id::RoleId;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, SerenityErrorExt,
};
use crate::service::iam_role::IAMRoleService;
use crate::service::Services;

pub struct IAMCommand;

struct IAMCommandOptions {
    role_id: RoleId,
}

enum IAMCommandOptionFailure {
    MissingOption,
}

fn generate_options(data: &CommandData) -> Result<IAMCommandOptions, IAMCommandOptionFailure> {
    let role = if let Some(role) = data.role("role") {
        role
    } else {
        return Err(IAMCommandOptionFailure::MissingOption);
    };

    Ok(IAMCommandOptions { role_id: role.id })
}

#[async_trait]
impl SlashCommand for IAMCommand {
    fn command_name(&self) -> &'static str {
        "iam"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("iam")
            .kind(CommandType::ChatInput)
            .description("self assigns specified role")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .add_option(
                CreateCommandOption::new(CommandOptionType::Role, "role", "role to assign")
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
            member,
            permissions: _,
        } = verify_guild_slash_command(interaction)?;

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(IAMCommandOptionFailure::MissingOption) => {
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
            reply_to_interaction_str(
                &context.http,
                interaction,
                "Could not find a role with specified name!",
                true,
            )
            .await;
            return Ok(());
        }

        if let Err(err) = context
            .http
            .add_member_role(
                guild_id,
                member.user.id,
                options.role_id,
                Some("Member self-assigned the role"),
            )
            .await
        {
            if let Some(error_code) = err.discord_error_code() {
                let error_message = match error_code {
                    50013 => "Could not assign specified role. Do I have enough permissions?",
                    10011 => "This role no longer exists!",
                    _ => bail!("failed to issue discord member role add {}", err),
                };

                reply_to_interaction_str(&context.http, interaction, error_message, true).await;
            }
        } else {
            reply_to_interaction_str(&context.http, interaction, "Assigned!", true).await;
        }

        Ok(())
    }
}
