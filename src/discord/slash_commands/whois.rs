use anyhow::bail;
use async_trait::async_trait;
use serenity::all::{CommandData, CommandInteraction, CommandOptionType, CommandType};
use serenity::builder::{CreateCommand, CreateCommandOption, CreateEmbed, CreateEmbedAuthor};
use serenity::client::Context;
use serenity::model::guild::PartialMember;
use serenity::model::user::User;

use crate::config::Config;
use crate::constants::EMBED_COLOR;
use crate::discord::slash_commands::whois::WhoisCommandOptionFailure::MissingOption;
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_embed, verify_guild_slash_command, CommandDataExt,
    GuildSlashCommandInteraction,
};
use crate::service::guild::{CachedGuild, GuildService};
use crate::service::Services;

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
        .author(CreateEmbedAuthor::new(known_as).icon_url(user.face()))
        .title(title)
        .field("ID", user.id.to_string(), false)
        .field("User Flags", flags, false)
        .field("Registered On", created_at, true)
        .field("Joined On", joined_at, true)
        .field("Boost Status", boost_status, true)
        .colour(EMBED_COLOR)
}

fn generate_user_embed(user: &User) -> CreateEmbed {
    let flags = match user.public_flags {
        Some(flags) => format!("{:?}", flags),
        None => "<none>".into(),
    };

    let created_at = format!("<t:{}>", user.created_at().unix_timestamp());

    CreateEmbed::default()
        .author(CreateEmbedAuthor::new(user.tag()).icon_url(user.face()))
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

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("whois")
            .kind(CommandType::ChatInput)
            .description("displays information about given user or server member")
            .dm_permission(false)
            .add_option(
                CreateCommandOption::new(CommandOptionType::User, "user", "target user to query")
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

            reply_to_interaction_embed(&context.http, interaction, embed, true).await;
        } else {
            let embed = generate_user_embed(user);

            reply_to_interaction_embed(&context.http, interaction, embed, true).await;
        }

        Ok(())
    }
}
