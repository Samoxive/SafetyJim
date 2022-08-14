use anyhow::bail;
use async_trait::async_trait;
use serenity::builder::{
    CreateApplicationCommand, CreateEmbed, CreateEmbedAuthor, CreateEmbedFooter,
    CreateInteractionResponse, CreateInteractionResponseData,
};
use serenity::client::bridge::gateway::ShardId;
use serenity::client::Context;
use serenity::model::application::interaction::application_command::ApplicationCommandInteraction;
use serenity::model::application::interaction::InteractionResponseType;
use serenity::model::channel::MessageFlags;
use tracing::error;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::constants::{
    AVATAR_URL, EMBED_COLOR, GITHUB_LINK, INVITE_LINK, START_EPOCH, SUPPORT_SERVER_INVITE_LINK,
};
use crate::discord::slash_commands::SlashCommand;
use crate::service::ban::BanService;
use crate::service::guild_statistic::GuildStatisticService;
use crate::service::shard_statistic::ShardStatisticService;
use crate::util::now;

pub struct InfoCommand;

#[async_trait]
impl SlashCommand for InfoCommand {
    fn command_name(&self) -> &'static str {
        "info"
    }

    fn create_command(&self) -> CreateApplicationCommand {
        CreateApplicationCommand::default()
            .name("info")
            .description("displays information about Jim")
            .dm_permission(false)
    }

    async fn handle_command(
        &self,
        context: &Context,
        interaction: &ApplicationCommandInteraction,
        _config: &Config,
        services: &TypeMap,
    ) -> anyhow::Result<()> {
        let shard_id = context.shard_id;
        let shard_info = if let Some(service) = services.get::<ShardStatisticService>() {
            service.get_shard_latency_info(ShardId(shard_id)).await
        } else {
            bail!("couldn't get shard statistic service!");
        };

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

        let shard_string = format!("[{} / {}]", shard_id, shard_info.total_shard_count);

        let embed = CreateEmbed::default()
            .author(
                CreateEmbedAuthor::default()
                    .name(format!("Safety Jim - {}", shard_string))
                    .icon_url(AVATAR_URL),
            )
            .description(format!(
                "Lifting the :hammer: since {}",
                START_EPOCH.get().expect("")
            ))
            .field(
                "Server Count",
                &guild_statistics.guild_count.to_string(),
                true,
            )
            .field(
                "User Count",
                &guild_statistics.member_count.to_string(),
                true,
            )
            .field("\u{200E}", "\u{200E}", true)
            .field(
                "Websocket Ping",
                &format!(
                    "Shard {}: {}ms\nAverage: {}ms",
                    shard_string,
                    shard_info.current_shard_latency,
                    shard_info.average_shard_latency
                ),
                true,
            )
            .field("\u{200E}", "\u{200E}", true)
            .field("\u{200E}", "\u{200E}", true)
            .field(
                "Links",
                &format!(
                    "[Support]({}) | [Github]({}) | [Invite]({})",
                    SUPPORT_SERVER_INVITE_LINK, GITHUB_LINK, INVITE_LINK
                ),
                true,
            )
            .footer(CreateEmbedFooter::default().text(format!(
                "Made by Samoxive#8634 | Days since last incident: {}",
                days_since_last_ban
            )))
            .color(EMBED_COLOR);

        let response = CreateInteractionResponse::default()
            .kind(InteractionResponseType::ChannelMessageWithSource)
            .interaction_response_data(
                CreateInteractionResponseData::default()
                    .flags(MessageFlags::EPHEMERAL)
                    .add_embed(embed),
            );

        interaction
            .create_interaction_response(&context.http, response)
            .await
            .map_err(|err| {
                error!("failed to reply to interaction {}", err);
                err
            })?;

        Ok(())
    }
}
