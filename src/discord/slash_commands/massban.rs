use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    invisible_failure_reply, invisible_success_reply, unauthorized_reply,
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
    SerenityErrorExt, UserExt,
};
use crate::service::hardban::{HardbanFailure, HardbanService};
use crate::service::setting::SettingService;
use anyhow::bail;
use serenity::model::id::UserId;
use serenity::model::Permissions;
use crate::service::guild::GuildService;

pub struct MassbanCommand;

struct MassbanCommandOptions {
    target_users: Vec<UserId>,
}

enum MassbanCommandOptionFailure {
    MissingOption,
    UserIdParsingFailed,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<MassbanCommandOptions, MassbanCommandOptionFailure> {
    let users_str = if let Some(s) = data.string("users") {
        s
    } else {
        return Err(MassbanCommandOptionFailure::MissingOption);
    };

    let (user_ids, errors): (Vec<_>, Vec<_>) = users_str
        .split(',')
        .map(|element| element.parse::<u64>())
        .partition(Result::is_ok);

    if !errors.is_empty() {
        return Err(MassbanCommandOptionFailure::UserIdParsingFailed);
    }

    let user_ids: Vec<UserId> = user_ids
        .into_iter()
        .map(|result| result.unwrap())
        .map(UserId)
        .collect();

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

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("massban")
            .description("hardbans given users in mass")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("users")
                    .description("comma separated ids of users to hardban")
                    .kind(ApplicationCommandOptionType::String)
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

        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            bail!("couldn't get guild service!");
        };

        let setting = setting_service.get_setting(guild_id).await;

        let guild = guild_service.get_guild(guild_id).await?;

        let mut mod_log_failure = None;
        for target_user_id in options.target_users {
            // massban is used for cases like raids, so getting the users from cache would unnecessarily
            // inflate the cache that is mostly used for the front end, plus we are likely to send all
            // these requests because they aren't likely to be in the cache in the first place
            let target_user = match context.http.get_user(target_user_id.0).await {
                Ok(user) => user,
                Err(err) => match err.discord_error_code() {
                    Some(10013) => {
                        invisible_failure_reply(
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
                    invisible_failure_reply(
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
                    invisible_failure_reply(
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
            invisible_failure_reply(&context.http, interaction, err.to_interaction_response())
                .await;
            return Ok(());
        }

        invisible_success_reply(&context.http, interaction, "Success.").await;
        Ok(())
    }
}
