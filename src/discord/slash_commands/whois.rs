use async_trait::async_trait;
use serenity::builder::{CreateApplicationCommand, CreateEmbed};
use serenity::client::Context;
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData, ApplicationCommandOptionType,
};

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::constants::EMBED_COLOR;
use crate::discord::slash_commands::whois::WhoisCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    verify_guild_slash_command, ApplicationCommandInteractionDataExt, GuildSlashCommandInteraction,
};
use anyhow::bail;
use serenity::model::guild::PartialMember;
use serenity::model::interactions::InteractionResponseType;
use serenity::model::prelude::InteractionApplicationCommandCallbackDataFlags;
use serenity::model::user::User;
use tracing::error;
use crate::service::guild::{CachedGuild, GuildService};

pub struct WhoisCommand;

struct WhoisCommandOptions<'a> {
    target: (&'a User, Option<&'a PartialMember>),
}

enum WhoisCommandOptionFailure {
    MissingOption,
}

fn generate_options(
    data: &ApplicationCommandInteractionData,
) -> Result<WhoisCommandOptions, WhoisCommandOptionFailure> {
    let target = if let Some(target) = data.user("user") {
        target
    } else {
        return Err(MissingOption);
    };

    Ok(WhoisCommandOptions { target })
}

fn generate_member_embed<'a>(
    embed: &'a mut CreateEmbed,
    guild: &CachedGuild,
    member: &PartialMember,
    user: &User,
) -> &'a mut CreateEmbed {
    let boost_status = match member.premium_since {
        Some(time) => format!("Since <t:{}>", time.timestamp()),
        None => "Not Boosting".into(),
    };

    let flags = match user.public_flags {
        Some(flags) => format!("{:?}", flags),
        None => "<none>".into(),
    };

    let known_as = match &member.nick {
        Some(nick) => format!("{} - {}", user.tag(), nick),
        None => user.tag(),
    };

    let title = if guild.owner_id == user.id {
        format!("Owner of {}", guild.name)
    } else {
        format!("Member of {}", guild.name)
    };

    let created_at = format!("<t:{}>", user.created_at().timestamp());
    let joined_at = match &member.joined_at {
        Some(time) => format!("<t:{}>", time.timestamp()),
        None => "<unknown>".into(),
    };

    embed
        .author(|author| author.name(known_as).icon_url(user.face()))
        .title(title)
        .field("ID", user.id.to_string(), false)
        .field("User Flags", flags, false)
        .field("Registered On", created_at, true)
        .field("Joined On", joined_at, true)
        .field("Boost Status", boost_status, true)
        .colour(EMBED_COLOR)
}

fn generate_user_embed<'a>(embed: &'a mut CreateEmbed, user: &User) -> &'a mut CreateEmbed {
    let flags = match user.public_flags {
        Some(flags) => format!("{:?}", flags),
        None => "<none>".into(),
    };

    let created_at = format!("<t:{}>", user.created_at().timestamp());

    embed
        .author(|author| author.name(user.tag()).icon_url(user.face()))
        .title("Discord User")
        .field("ID", user.id.to_string(), false)
        .field("User Flags", flags, false)
        .field("Registered On", created_at, false)
        .colour(EMBED_COLOR)
}

#[async_trait]
impl SlashCommand for WhoisCommand {
    fn command_name(&self) -> &'static str {
        "whois"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("whois")
            .description("displays information about given user or server member")
            .default_permission(true)
            .create_option(|option| {
                option
                    .name("user")
                    .description("target user to query")
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
            permissions: _,
        } = verify_guild_slash_command(interaction)?;

        let options = match generate_options(&interaction.data) {
            Ok(options) => options,
            Err(MissingOption) => {
                bail!("interaction has missing data options")
            }
        };

        let (user, member_option) = options.target;

        if let Some(member) = member_option {
            let guild_service = if let Some(service) = services.get::<GuildService>() {
                service
            } else {
                bail!("couldn't get guild service!");
            };

            let guild = guild_service.get_guild(guild_id).await?;

            interaction
                .create_interaction_response(&context.http, |response| {
                    response
                        .kind(InteractionResponseType::ChannelMessageWithSource)
                        .interaction_response_data(|message| {
                            message
                                .create_embed(|embed| {
                                    generate_member_embed(embed, &guild, member, user)
                                })
                                .flags(InteractionApplicationCommandCallbackDataFlags::EPHEMERAL)
                        })
                })
                .await
                .map_err(|err| {
                    error!("failed to reply to interaction {}", err);
                    err
                })?;
        } else {
            interaction
                .create_interaction_response(&context.http, |response| {
                    response
                        .kind(InteractionResponseType::ChannelMessageWithSource)
                        .interaction_response_data(|message| {
                            message
                                .create_embed(|embed| generate_user_embed(embed, user))
                                .flags(InteractionApplicationCommandCallbackDataFlags::EPHEMERAL)
                        })
                })
                .await
                .map_err(|err| {
                    error!("failed to reply to interaction {}", err);
                    err
                })?;
        }

        Ok(())
    }
}
