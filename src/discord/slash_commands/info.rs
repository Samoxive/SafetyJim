use anyhow::bail;
use async_trait::async_trait;
use serenity::all::Context;
use serenity::all::{CommandInteraction, CommandType, InstallationContext, InteractionContext};
use serenity::builder::{CreateCommand, CreateEmbed, CreateEmbedAuthor, CreateEmbedFooter};

use crate::config::Config;
use crate::constants::{
    AVATAR_URL, EMBED_COLOR, GITHUB_LINK, INVITE_LINK, START_EPOCH, SUPPORT_SERVER_INVITE_LINK,
};
use crate::discord::slash_commands::SlashCommand;
use crate::discord::util::reply_to_interaction_embed;
use crate::service::ban::BanService;
use crate::service::guild_statistic::GuildStatisticService;
use crate::service::Services;
use crate::util::now;

pub struct InfoCommand;

#[async_trait]
impl SlashCommand for InfoCommand {
    fn command_name(&self) -> &'static str {
        "info"
    }

    fn create_command(&self) -> CreateCommand {
        CreateCommand::new("info")
            .kind(CommandType::ChatInput)
            .description("displays information about Jim")
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
        let shard_id = context.shard_id;

        let guild_statistics = if let Some(service) = services.get::<GuildStatisticService>() {
            service.get_guild_statistics().await
        } else {
            bail!("couldn't get guild statistic service!");
        };

        let guild_id = if let Some(guild_id) = interaction.guild_id {
            guild_id
        } else {
            bail!("interaction has missing guild id");
        };

        let last_ban = if let Some(service) = services.get::<BanService>() {
            service.fetch_last_guild_ban(guild_id).await
        } else {
            bail!("couldn't get ban service!");
        };

        let days_since_last_ban = if let Some(last_ban) = last_ban {
            let day_count = (now() - (last_ban.ban_time as u64)) / (60 * 60 * 24);
            day_count.to_string()
        } else {
            "\u{221E}".to_string()
        };

        let shard_string = format!("[{}]", shard_id);

        let embed = CreateEmbed::default()
            .author(
                CreateEmbedAuthor::new(format!("Safety Jim - {}", shard_string))
                    .icon_url(AVATAR_URL),
            )
            .description(format!(
                "Lifting the :hammer: since {}",
                START_EPOCH.get().expect("")
            ))
            .field(
                "Server Count",
                guild_statistics.guild_count.to_string(),
                true,
            )
            .field(
                "User Count",
                guild_statistics.member_count.to_string(),
                true,
            )
            .field("\u{200E}", "\u{200E}", true)
            .field(
                "Websocket Ping",
                format!(
                    "Shard {}",
                    shard_string,
                ),
                true,
            )
            .field("\u{200E}", "\u{200E}", true)
            .field("\u{200E}", "\u{200E}", true)
            .field(
                "Links",
                format!(
                    "[Support]({}) | [Github]({}) | [Invite]({})",
                    SUPPORT_SERVER_INVITE_LINK, GITHUB_LINK, INVITE_LINK
                ),
                true,
            )
            .footer(CreateEmbedFooter::new(format!(
                "Made by Samoxive#8634 | Days since last incident: {}",
                days_since_last_ban
            )))
            .color(EMBED_COLOR);

        reply_to_interaction_embed(&context.http, interaction, embed, true).await;

        Ok(())
    }
}
