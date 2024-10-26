use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{CommandInteraction, CommandType, InstallationContext, InteractionContext};
use serenity::builder::{CreateCommand, CreateEmbed, CreateEmbedAuthor};
use tracing::error;

use crate::config::Config;
use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{
    reply_to_interaction_embed, verify_guild_slash_command, GuildSlashCommandInteraction,
};
use crate::service::guild::GuildService;
use crate::service::Services;

pub struct ServerCommand;

#[async_trait]
impl SlashCommand for ServerCommand {
    fn command_name(&self) -> &'static str {
        "server"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("server")
            .kind(CommandType::ChatInput)
            .description("displays information about the server")
            .add_integration_type(InstallationContext::Guild)
            .add_context(InteractionContext::Guild)
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

        let guild_service = if let Some(service) = services.get::<GuildService>() {
            service
        } else {
            bail!("couldn't get guild service!");
        };

        // can't use cache because we need all the information about the guild
        let guild = guild_id
            .to_partial_guild_with_counts(&context.http)
            .await
            .map_err(|err| {
                error!("failed to fetch partial guild with counts {}", err);
                err
            })?;

        let owner = guild_service.get_user(guild.owner_id).await?;

        let vanity_url = guild
            .vanity_url_code
            .as_ref()
            .map(|code| format!("https://discord.gg/{}", code));

        let icon_url = guild.icon_url();
        let embed = CreateEmbed::default()
            .author(
                CreateEmbedAuthor::new(format!("{} ({})", guild.name, guild.id))
                    .url(vanity_url.unwrap_or_else(|| "".into()))
                    .icon_url(icon_url.as_deref().unwrap_or(AVATAR_URL)),
            )
            .colour(EMBED_COLOR)
            .field(
                "Server Owner",
                format!("{} ({})", owner.tag, guild.owner_id),
                true,
            )
            .field(
                "Member Count",
                format!("{}", guild.approximate_member_count.unwrap_or_default()),
                true,
            )
            .field(
                "Created On",
                format!("<t:{}>", guild.id.created_at().unix_timestamp()),
                true,
            )
            .field(
                "Boost Count",
                guild
                    .premium_subscription_count
                    .unwrap_or_default()
                    .to_string(),
                true,
            )
            .field("Boost Tier", format!("{:?}", guild.premium_tier), true)
            .field("NSFW Tier", format!("{:?}", guild.nsfw_level), true)
            .field(
                "Server Features",
                guild
                    .features
                    .iter()
                    .map(|s| s.as_str())
                    .collect::<Vec<&str>>()
                    .join(" | "),
                false,
            );

        reply_to_interaction_embed(&context.http, interaction, embed, true).await;

        Ok(())
    }
}
