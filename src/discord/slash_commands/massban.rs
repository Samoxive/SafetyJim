use std::num::{NonZeroU64, ParseIntError};

use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{
    CreateCommand, CreateCommandOption, CreateInteractionResponse, CreateInteractionResponseMessage,
};
use serenity::client::Context;
use serenity::model::application::command::{CommandOptionType, CommandType};
use serenity::model::application::interaction::application_command::{
    CommandData, CommandInteraction,
};
use serenity::model::id::UserId;
use serenity::model::Permissions;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::constants::JIM_ID;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    edit_interaction_response, invisible_failure_reply, unauthorized_reply,
    verify_guild_slash_command, CommandDataExt, GuildSlashCommandInteraction, SerenityErrorExt,
    UserExt,
};
use crate::service::guild::GuildService;
use crate::service::hardban::{HardbanFailure, HardbanService};
use crate::service::setting::SettingService;

pub struct MassbanCommand;

struct MassbanCommandOptions {
    target_users: Vec<UserId>,
}

enum MassbanCommandOptionFailure {
    MissingOption,
    UserIdParsingFailed,
}

fn generate_options(
    data: &CommandData,
) -> Result<MassbanCommandOptions, MassbanCommandOptionFailure> {
    let users_str = if let Some(s) = data.string("users") {
        s
    } else {
        return Err(MassbanCommandOptionFailure::MissingOption);
    };

    let user_ids = match users_str
        .split(',')
        .map(|element| element.trim())
        .map(|element| element.parse::<NonZeroU64>())
        .collect::<Result<Vec<NonZeroU64>, ParseIntError>>()
    {
        Ok(ids) => ids,
        Err(_) => return Err(MassbanCommandOptionFailure::UserIdParsingFailed),
    };

    let user_ids: Vec<UserId> = user_ids.into_iter().map(UserId).collect();

    Ok(MassbanCommandOptions {
        target_users: user_ids,
    })
}

fn is_authorized(permissions: Permissions) -> bool {
    permissions.administrator() || permissions.ban_members()
}

#[async_trait]
impl SlashCommand for MassbanCommand {
    fn command_name(&self) -> &'static str {
        "massban"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("massban")
            .kind(CommandType::ChatInput)
            .description("hardbans given users in mass")
            .dm_permission(false)
            .default_member_permissions(Permissions::BAN_MEMBERS)
            .add_option(
                CreateCommandOption::new(
                    CommandOptionType::String,
                    "users",
                    "comma separated ids of users to hardban",
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

        let channel_id = interaction.channel_id;
        let mod_user = &interaction.user;

        if !is_authorized(permissions) {
            unauthorized_reply(&*context.http, interaction, Permissions::BAN_MEMBERS).await;
            return Ok(());
        }

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MassbanCommandOptionFailure::UserIdParsingFailed) => {
                invisible_failure_reply(
                    &*context.http,
                    interaction,
                    "Failed to understand given user ids!",
                )
                .await;
                return Ok(());
            }
            Err(MassbanCommandOptionFailure::MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        if options
            .target_users
            .iter()
            .any(|target| *target == mod_user.id)
        {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You can't massban yourself, dummy!",
            )
            .await;
            return Ok(());
        }

        if options.target_users.iter().any(|target| *target == JIM_ID) {
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

        if options
            .target_users
            .iter()
            .any(|target| *target == guild.owner_id)
        {
            invisible_failure_reply(
                &*context.http,
                interaction,
                "You can't massban owner of the server!",
            )
            .await;
            return Ok(());
        }

        let hardban_service = if let Some(service) = services.get::<HardbanService>() {
            service
        } else {
            bail!("couldn't get hardban service!")
        };

        let setting_service = if let Some(service) = services.get::<SettingService>() {
            service
        } else {
            bail!("couldn't get setting service!");
        };

        let response =
            CreateInteractionResponse::Defer(CreateInteractionResponseMessage::default());

        interaction
            .create_interaction_response(&context.http, response)
            .await?;

        let setting = setting_service.get_setting(guild_id).await;

        let mut mod_log_failure = None;
        for target_user_id in options.target_users {
            // massban is used for cases like raids, so getting the users from cache would unnecessarily
            // inflate the cache that is mostly used for the front end, plus we are likely to send all
            // these requests because they aren't likely to be in the cache in the first place
            let target_user = match context.http.get_user(target_user_id).await {
                Ok(user) => user,
                Err(err) => match err.discord_error_code() {
                    Some(10013) => {
                        edit_interaction_response(
                            &*context.http,
                            interaction,
                            &format!("Couldn't find user for given id: {}.", target_user_id.0),
                        )
                        .await;
                        return Ok(());
                    }
                    _ => bail!("failed to fetch massban user!"),
                },
            };

            match hardban_service
                .issue_hardban(
                    &context.http,
                    guild_id,
                    &guild.name,
                    &setting,
                    Some(channel_id),
                    mod_user.id,
                    &mod_user.tag_and_id(),
                    &target_user,
                    "Targeted in mass ban".into(),
                )
                .await
            {
                Ok(_) => (),
                Err(HardbanFailure::Unauthorized) => {
                    edit_interaction_response(
                        &context.http,
                        interaction,
                        "I don't have enough permissions to do this action!",
                    )
                    .await;
                    return Ok(());
                }
                Err(HardbanFailure::ModLogError(err)) => {
                    mod_log_failure = Some(err);
                }
                Err(HardbanFailure::Unknown) => {
                    edit_interaction_response(
                        &context.http,
                        interaction,
                        "Could not massban one of specified users for unknown reasons, this incident has been logged.",
                    )
                        .await;
                    return Ok(());
                }
            }
        }

        if let Some(err) = mod_log_failure {
            edit_interaction_response(&context.http, interaction, err.to_interaction_response())
                .await;
            return Ok(());
        }

        edit_interaction_response(&context.http, interaction, "Success.").await;
        Ok(())
    }
}
