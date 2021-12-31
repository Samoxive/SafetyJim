use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use crate::config::Config;
use crate::constants::JIM_ID;
use crate::discord::slash_commands::kick::KickCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
    UserExt,
};
use crate::service::guild::GuildService;
use crate::service::kick::{KickFailure, KickService};
use crate::service::setting::SettingService;
use anyhow::bail;
use serenity::model::user::User;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

pub struct KickCommand;

struct KickCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
}

enum KickCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<KickCommandOptions, KickCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    let reason = data.string("reason").map(String::from);

    Ok(KickCommandOptions {
        target_user,
        reason,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.kick_members()
}

#[async_trait]
impl SlashCommand for KickCommand {
    fn command_name(&self) -> &'static str {
        "kick"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("kick")
            .description("kicks given user")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("user")
                    .description("target user to kick")
                    .kind(ApplicationCommandOptionType::User)
                    .required(true)
            })
            .create_option(|option| {
                option
                    .name("reason")
                    .description("reason for the kick")
                    .kind(ApplicationCommandOptionType::String)
                    .required(false)
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

        let channel_id = interaction.channel_id;
        let mod_user = &interaction.user;

        if !is_authorized(permissions) {
            unauthorized_reply(&*context.http, interaction, Permissions::KICK_MEMBERS).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        if options.target_user.id == mod_user.id {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You can't kick yourself, dummy!",
            )
            .await;
            return Ok(());
        }

        if options.target_user.id == JIM_ID {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "I'm sorry, Dave. I'm afraid I can't do that.",
            )
            .await;
            return Ok(());
        }

        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            bail!("couldn't get guild service!");
        };

        let guild = guild_service.get_guild(guild_id).await?;

        if options.target_user.id == guild.owner_id {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You can't kick owner of the server!",
            )
            .await;
            return Ok(());
        }

        let kick_service = if let Some(service) = services.get::<KickService>() {
            service
        } else {
            bail!("couldn't get kick service!");
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        match kick_service
            .issue_kick(
                &context.http,
                guild_id,
                &guild.name,
                &setting,
                services,
                Some(channel_id),
                mod_user.id,
                &mod_user.tag_and_id(),
                options.target_user,
                options
                    .reason
                    .unwrap_or_else(|| "No reason specified".into()),
                0,
            )
            .await
        {
            Ok(_) => {
                invisible_success_reply(&context.http, interaction, "Success.").await;
            }
            Err(KickFailure::Unauthorized) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                )
                .await;
            }
            Err(KickFailure::ModLogError(err)) => {
                invisible_failure_reply(&context.http, interaction, err.to_interaction_response())
                    .await;
            }
            Err(KickFailure::Unknown) => {
                invisible_failure_reply(
                    &context.http,
                    interaction,
                    "Could not kick specified user for unknown reasons, this incident has been logged.",
                )
                    .await;
            }
        }

        Ok(())
    }
}
