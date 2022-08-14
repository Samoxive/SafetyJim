use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{
    CreateApplicationCommand, CreateApplicationCommandOption, CreateEmbed, CreateEmbedAuthor,
    CreateInteractionResponse, CreateInteractionResponseData,
};
use serenity::client::Context;
use serenity::model::application::command::CommandOptionType;
use serenity::model::application::interaction::application_command::{
    ApplicationCommandInteraction, CommandData,
};
use serenity::model::application::interaction::InteractionResponseType;
use serenity::model::channel::MessageFlags;
use serenity::model::guild::PartialMember;
use serenity::model::user::User;
use tracing::error;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::constants::EMBED_COLOR;
use crate::discord::slash_commands::whois::WhoisCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    verify_guild_slash_command, CommandDataExt, GuildSlashCommandInteraction,
};
use crate::service::guild::{CachedGuild, GuildService};

pub struct WhoisCommand;

struct WhoisCommandOptions<'a> {
    target: (&'a User, Option<&'a PartialMember>),
}

enum WhoisCommandOptionFailure {
    MissingOption,
}

fn generate_options(data: &CommandData) -> Result<WhoisCommandOptions, WhoisCommandOptionFailure> {
    let target = if let Some(target) = data.user("user") {
        target
    } else {
        return Err(MissingOption);
    };

    Ok(WhoisCommandOptions { target })
}

fn generate_member_embed(guild: &CachedGuild, member: &PartialMember, user: &User) -> CreateEmbed {
    let boost_status = match member.premium_since {
        Some(time) => format!("Since <t:{}>", time.unix_timestamp()),
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

    let created_at = format!("<t:{}>", user.created_at().unix_timestamp());
    let joined_at = match &member.joined_at {
        Some(time) => format!("<t:{}>", time.unix_timestamp()),
        None => "<unknown>".into(),
    };

    CreateEmbed::default()
        .author(
            CreateEmbedAuthor::default()
                .name(known_as)
                .icon_url(user.face()),
        )
        .title(title)
        .field("ID", &user.id.to_string(), false)
        .field("User Flags", &flags, false)
        .field("Registered On", &created_at, true)
        .field("Joined On", &joined_at, true)
        .field("Boost Status", &boost_status, true)
        .colour(EMBED_COLOR)
}

fn generate_user_embed(user: &User) -> CreateEmbed {
    let flags = match user.public_flags {
        Some(flags) => format!("{:?}", flags),
        None => "<none>".into(),
    };

    let created_at = format!("<t:{}>", user.created_at().unix_timestamp());

    CreateEmbed::default()
        .author(
            CreateEmbedAuthor::default()
                .name(user.tag())
                .icon_url(user.face()),
        )
        .title("Discord User")
        .field("ID", &user.id.to_string(), false)
        .field("User Flags", &flags, false)
        .field("Registered On", &created_at, false)
        .colour(EMBED_COLOR)
}

#[async_trait]
impl SlashCommand for WhoisCommand {
    fn command_name(&self) -> &'static str {
        "whois"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::default()
            .name("whois")
            .description("displays information about given user or server member")
            .dm_permission(false)
            .add_option(
                CreateApplicationCommandOption::default()
                    .name("user")
                    .description("target user to query")
                    .kind(CommandOptionType::User)
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

            let embed = generate_member_embed(&guild, member, user);

            let data = CreateInteractionResponseData::default()
                .flags(MessageFlags::EPHEMERAL)
                .add_embed(embed);

            let response = CreateInteractionResponse::default()
                .kind(InteractionResponseType::ChannelMessageWithSource)
                .interaction_response_data(data);

            interaction
                .create_interaction_response(&context.http, response)
                .await
                .map_err(|err| {
                    error!("failed to reply to interaction {}", err);
                    err
                })?;
        } else {
            let embed = generate_user_embed(user);

            let data = CreateInteractionResponseData::default()
                .flags(MessageFlags::EPHEMERAL)
                .add_embed(embed);

            let response = CreateInteractionResponse::default()
                .kind(InteractionResponseType::ChannelMessageWithSource)
                .interaction_response_data(data);

            interaction
                .create_interaction_response(&context.http, response)
                .await
                .map_err(|err| {
                    error!("failed to reply to interaction {}", err);
                    err
                })?;
        }

        Ok(())
    }
}
