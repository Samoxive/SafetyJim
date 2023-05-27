use anyhow::bail;
use async_trait::async_trait;
use serenity::all::{CommandData, CommandInteraction, CommandOptionType, CommandType};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::client::Context;
use serenity::model::user::User;
use serenity::model::Permissions;

use crate::config::Config;
use crate::discord::slash_commands::unban::UnbanCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, UserExt,
};
use crate::service::ban::{BanService, UnbanFailure};
use crate::service::Services;

pub struct UnbanCommand;

struct UnbanCommandOptions<'a> {
    target_user: &'a User,
}

enum UnbanCommandOptionFailure {
    MissingOption,
}

fn generate_options(data: &CommandData) -> Result<UnbanCommandOptions, UnbanCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    Ok(UnbanCommandOptions { target_user })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.ban_members()
}

#[async_trait]
impl SlashCommand for UnbanCommand {
    fn command_name(&self) -> &'static str {
        "unban"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("unban")
            .kind(CommandType::ChatInput)
            .description("unbans given user")
            .dm_permission(false)
            .default_member_permissions(Permissions::BAN_MEMBERS)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to unban")
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
            unauthorized_reply(&context.http, interaction, Permissions::BAN_MEMBERS).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let ban_service = if let Some(service) = services.get::<BanService>() {
            service
        } else {
            bail!("couldn't get ban service!");
        };

        match ban_service
            .unban(
                &context.http,
                guild_id,
                options.target_user.id,
                &mod_user.tag_and_id(),
            )
            .await
        {
            Ok(_) => {
                reply_to_interaction_str(&context.http, interaction, "Success.", true).await;
            }
            Err(UnbanFailure::UserNotBanned) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Specified user is not banned!",
                    true,
                )
                .await;
            }
            Err(UnbanFailure::Unauthorized) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                    true,
                )
                .await;
            }
            Err(UnbanFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Could not unban specified user for unknown reasons, this incident has been logged.",
                    true,
                )
                    .await;
            }
        }

        Ok(())
    }
}
