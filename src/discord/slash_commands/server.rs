use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::CreateApplicationCommand;
use serenity::client::Context;

use serenity::prelude::TypeMap;

use crate::config::Config;
use crate::constants::{AVATAR_URL, EMBED_COLOR};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::{verify_guild_slash_command, GuildSlashCommandInteraction};
use crate::service::guild::GuildService;
use serenity::model::interactions::application_command::ApplicationCommandInteraction;
use serenity::model::interactions::{
    InteractionApplicationCommandCallbackDataFlags, InteractionResponseType,
};
use tracing::error;

pub struct ServerCommand;

#[async_trait]
impl SlashCommand for ServerCommand {
    fn command_name(&self) -> &'static str {
        "server"
    }

    fn create_command<'a>(
        &self,
        command: &'a mut CreateApplicationCommand,
    ) -> &'a mut CreateApplicationCommand {
        command
            .name("server")
            .description("displays information about the server")
            .default_permission(true)
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

        interaction
            .create_interaction_response(&context.http, |response| {
                response
                    .kind(InteractionResponseType::ChannelMessageWithSource)
                    .interaction_response_data(|message| {
                        message
                            .create_embed(|embed| {
                                embed
                                    .author(|author| {
                                        author
                                            .name(format!("{} ({})", guild.name, guild.id))
                                            .url(vanity_url.unwrap_or_else(|| "".into()))
                                            .icon_url(
                                                guild.icon_url().as_deref().unwrap_or(AVATAR_URL),
                                            )
                                    })
                                    .colour(EMBED_COLOR)
                                    .field(
                                        "Server Owner",
                                        format!("{} ({})", owner.tag, guild.owner_id),
                                        true,
                                    )
                                    .field(
                                        "Member Count",
                                        format!("{}", guild.approximate_member_count.unwrap_or(0)),
                                        true,
                                    )
                                    .field(
                                        "Created On",
                                        format!("<t:{}>", guild.id.created_at().timestamp()),
                                        true,
                                    )
                                    .field(
                                        "Boost Count",
                                        guild.premium_subscription_count.to_string(),
                                        true,
                                    )
                                    .field("Boost Tier", format!("{:?}", guild.premium_tier), true)
                                    .field("NSFW Tier", format!("{:?}", guild.nsfw_level), true)
                                    .field("Server Features", guild.features.join(" | "), false)
                            })
                            .flags(InteractionApplicationCommandCallbackDataFlags::EPHEMERAL)
                    })
            })
            .await
            .map_err(|err| {
                error!("failed to reply to interaction {}", err);
                err
            })?;

        Ok(())
    }
}
