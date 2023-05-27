use anyhow::bail;
use async_trait::async_trait;
use serenity::all::{CommandData, CommandInteraction, CommandOptionType, CommandType};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::client::Context;
use serenity::model::user::User;
use serenity::model::Permissions;

use crate::config::Config;
use crate::discord::slash_commands::unmute::UnmuteCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, UserExt,
};
use crate::service::mute::{MuteService, UnmuteFailure};
use crate::service::Services;

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

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("unmute")
            .kind(CommandType::ChatInput)
            .description("unmutes given user")
            .dm_permission(false)
            .default_member_permissions(Permissions::MANAGE_ROLES)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to unmute")
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

        let mod_user = &interaction.user;

        if !is_authorized(permissions) {
            unauthorized_reply(&context.http, interaction, Permissions::MANAGE_ROLES).await;
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
                reply_to_interaction_str(&context.http, interaction, "Success.", true).await;
            }
            Err(UnmuteFailure::RoleNotFound) => {
                reply_to_interaction_str(&context.http, interaction, "Could not find a role called Muted, please create one yourself or mute a user to set it up automatically.", true).await;
            }
            Err(UnmuteFailure::Unauthorized) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                    true,
                )
                .await;
            }
            Err(UnmuteFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Could not unmute specified user for unknown reasons, this incident has been logged.",
                    true,
                )
                    .await;
            }
        }

        Ok(())
    }
}
