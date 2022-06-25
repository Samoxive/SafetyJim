use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
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

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("iam")
            .description("self assigns specified role")
            .dm_permission(false)
            .create_option(|option| {
                option
                    .name("role")
                    .description("role to assign")
                    .kind(CommandOptionType::Role)
                    .required(true)
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
            .add_member_role(
                guild_id.0.get(),
                member.user.id.0.get(),
                options.role_id.0.get(),
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

                invisible_failure_reply(&context.http, interaction, error_message).await;
            }
        } else {
            invisible_success_reply(&context.http, interaction, "Assigned!").await;
        }

        Ok(())
    }
}
