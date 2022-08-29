use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{CreateApplicationCommand, CreateApplicationCommandOption};
use serenity::client::Context;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
};
use serenity::model::user::User;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::unmute::UnmuteCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, CommandDataExt, GuildSlashCommandInteraction, UserExt,
};
use crate::service::mute::{MuteService, UnmuteFailure};

pub struct UnmuteCommand;

struct UnmuteCommandOptions<'a> {
    target_user: &'a User,
}

enum UnmuteCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<UnmuteCommandOptions, UnmuteCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    Ok(UnmuteCommandOptions { target_user })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.manage_roles()
}

#[async_trait]
impl SlashCommand for UnmuteCommand {
    fn command_name(&self) -> &'static str {
        "unmute"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::new("unmute")
            .description("unmutes given user")
            .dm_permission(false)
            .default_member_permissions(Permissions::MANAGE_ROLES)
            .add_option(
                CreateApplicationCommandOption::new(
                    CommandOptionType::User,
                    "user",
                    "target user to unmute",
                )
                .required(true),
            )
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

        let mod_user = &interaction.user;

        if !is_authorized(permissions) {
            unauthorized_reply(&*context.http, interaction, Permissions::MANAGE_ROLES).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let mute_service = if let Some(service) = services.get::<MuteService>() {
            service
        } else {
            bail!("couldn't get mute service!");
        };

        match mute_service
            .unmute(
                &context.http,
                services,
                guild_id,
                options.target_user.id,
                &mod_user.tag_and_id(),
            )
            .await
        {
            Ok(_) => {
                invisible_success_reply(&context.http, interaction, "Success.").await;
            }
            Err(UnmuteFailure::RoleNotFound) => {
                invisible_failure_reply(&context.http, interaction, "Could not find a role called Muted, please create one yourself or mute a user to set it up automatically.").await;
            }
            Err(UnmuteFailure::Unauthorized) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                )
                .await;
            }
            Err(UnmuteFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Could not unmute specified user for unknown reasons, this incident has been logged.",
                )
                    .await;
            }
        }

        Ok(())
    }
}
