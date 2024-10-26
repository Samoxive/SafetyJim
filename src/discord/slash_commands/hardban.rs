use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{
    CommandData, CommandInteraction, CommandOptionType, CommandType, InstallationContext,
    InteractionContext,
};
use serenity::builder::{CreateCommand, CreateCommandOption};
use serenity::model::user::User;
use serenity::model::Permissions;

use crate::config::Config;
use crate::constants::JIM_ID;
use crate::discord::slash_commands::hardban::HardbanCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_str, unauthorized_reply, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction, UserExt,
};
use crate::service::guild::GuildService;
use crate::service::hardban::{HardbanFailure, HardbanService};
use crate::service::setting::SettingService;
use crate::service::Services;

pub struct HardbanCommand;

struct HardbanCommandOptions<'a> {
    target_user: &'a User,
    reason: Option<String>,
}

enum HardbanCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &CommandData,
) -> Result<HardbanCommandOptions, HardbanCommandOptionFailure> {
    let target_user = if let Some((user, _)) = data.user("user") {
        user
    } else {
        return Err(MissingOption);
    };

    let reason = data.string("reason").map(String::from);

    Ok(HardbanCommandOptions {
        target_user,
        reason,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.ban_members()
}

#[async_trait]
impl SlashCommand for HardbanCommand {
    fn command_name(&self) -> &'static str {
        "hardban"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("hardban")
            .kind(CommandType::ChatInput)
            .description("hardbans given user, deleting all messages in last 7 days")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
            .default_member_permissions(Permissions::BAN_MEMBERS)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to hardban")
                    .required(true),
            )
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "reason",
                    "reason for the hardban",
                )
                .required(false),
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

        let channel_id = interaction.channel_id;
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

        if options.target_user.id == mod_user.id {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "You can't hardban yourself, dummy!",
                true,
            )
            .await;
            return Ok(());
        }

        if options.target_user.id == JIM_ID {
            reply_to_interaction_str(
                &context.http,
                interaction,
                "I'm sorry, Dave. I'm afraid I can't do that.",
                true,
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
            reply_to_interaction_str(
                &context.http,
                interaction,
                "You can't hardban owner of the server!",
                true,
            )
            .await;
            return Ok(());
        }

        let hardban_service = if let Some(service) = services.get::<HardbanService>() {
            service
        } else {
            bail!("couldn't get hardban service!");
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        match hardban_service
            .issue_hardban(
                &context.http,
                guild_id,
                &guild.name,
                &setting,
                Some(channel_id),
                mod_user.id,
                &mod_user.tag_and_id(),
                options.target_user,
                options
                    .reason
                    .unwrap_or_else(|| "No reason specified".into()),
            )
            .await
        {
            Ok(_) => {
                let _ =
                    reply_to_interaction_str(&context.http, interaction, "Success.", true).await;
            }
            Err(HardbanFailure::Unauthorized) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "I don't have enough permissions to do this action!",
                    true,
                )
                .await;
            }
            Err(HardbanFailure::ModLogError(err)) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    err.to_interaction_response(),
                    true,
                )
                .await;
            }
            Err(HardbanFailure::Unknown) => {
                reply_to_interaction_str(
                    &context.http,
                    interaction,
                    "Could not hardban specified user for unknown reasons, this incident has been logged.",
                    true,
                )
                    .await;
            }
        }

        Ok(())
    }
}
