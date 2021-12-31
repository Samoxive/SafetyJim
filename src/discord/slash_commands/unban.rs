use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::unban::UnbanCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
    UserExt,
};
use crate::service::ban::{BanService, UnbanFailure};
use anyhow::bail;
use serenity::model::user::User;
use serenity::model::Permissions;

pub struct UnbanCommand;

struct UnbanCommandOptions<'a> {
    target_user: &'a User,
}

enum UnbanCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<UnbanCommandOptions, UnbanCommandOptionFailure> {
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

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("unban")
            .description("unbans given user")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("user")
                    .description("target user to unban")
                    .kind(ApplicationCommandOptionType::User)
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
            member: _,
            permissions,
        } = verify_guild_slash_command(interaction)?;

        let mod_user = &interaction.user;

        if !is_authorized(permissions) {
            unauthorized_reply(&*context.http, interaction, Permissions::BAN_MEMBERS).await;
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
                invisible_success_reply(&context.http, interaction, "Success.").await;
            }
            Err(UnbanFailure::UserNotBanned) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Specified user is not banned!",
                )
                .await;
            }
            Err(UnbanFailure::Unauthorized) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                )
                .await;
            }
            Err(UnbanFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Could not unban specified user for unknown reasons, this incident has been logged.",
                )
                .await;
            }
        }

        Ok(())
    }
}
