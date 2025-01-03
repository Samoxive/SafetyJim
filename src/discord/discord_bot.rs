use std::collections::VecDeque;
use std::error::Error;
use std::num::{NonZeroU16, NonZeroU64};
use std::sync::Arc;
use std::time::Duration;

use serenity::all::{Command, CommandType, ComponentInteractionCollector, ComponentInteractionDataKind, Context, CreateCommand, CreateEmbed, CreateInteractionResponse, CreateInteractionResponseMessage, CreateSelectMenuOption, Event, EventHandler, Interaction, RawEventHandler};
use serenity::async_trait;
use serenity::builder::{
    AutocompleteChoice, CreateActionRow, CreateAutocompleteResponse, CreateEmbedFooter,
    CreateMessage, CreateSelectMenu, CreateSelectMenuKind, EditInteractionResponse,
};
use serenity::gateway::{ActivityData, ShardManager};
use serenity::http::Http;
use serenity::model::channel::{GuildChannel, Message, MessageType};
use serenity::model::event::{GuildMemberUpdateEvent, MessageUpdateEvent};
use serenity::model::gateway::{GatewayIntents, Ready};
use serenity::model::guild::{Guild, Member, PartialGuild, Role, UnavailableGuild};
use serenity::model::id::{ChannelId, GuildId, RoleId};
use serenity::model::user::{CurrentUser, User};
use serenity::model::Color;
use serenity::prelude::Mentionable;
use serenity::Client;
use simsearch::{SearchOptions, SimSearch};
use tokio::select;
use tokio::time::sleep;
use tracing::{error, warn};

use crate::config::Config;
use crate::constants::PROGRAMMING_LANGUAGES;
use crate::discord;
use crate::discord::message_processors::{get_all_processors, MessageProcessors};
use crate::discord::scheduled::run_scheduled_tasks;
use crate::discord::slash_commands::SlashCommands;
use crate::discord::util::{
    defer_interaction, edit_deferred_interaction_response, reply_to_interaction_str,
    verify_guild_message_create, verify_guild_message_update, CommandDataExt, GuildMessageCreated,
    GuildMessageUpdated, UserExt,
};
use crate::service::guild::GuildService;
use crate::service::guild_statistic::GuildStatisticService;
use crate::service::join::JoinService;
use crate::service::mute::MuteService;
use crate::service::setting::SettingService;
use crate::service::shard_statistic::ShardStatisticService;
use crate::service::tag::TagService;
use crate::service::watchdog::WatchdogService;
use crate::service::Services;
use crate::util::Shutdown;

const SHARD_LATENCY_POLLING_INTERVAL: u64 = 60;

pub struct DiscordBot {
    pub client: Client,
}

async fn feed_shard_statistics(
    shard_manager: Arc<ShardManager>,
    services: Arc<Services>,
    shutdown: Shutdown,
) {
    let mut receiver = shutdown.subscribe();
    let shard_statistic_service =
        if let Some(shard_statistic_service) = services.get::<ShardStatisticService>() {
            shard_statistic_service
        } else {
            return;
        };

    loop {
        select! {
            _ = sleep(Duration::from_secs(SHARD_LATENCY_POLLING_INTERVAL)) => {}
            _ = receiver.recv() => {
                return;
            }
        }

        shard_statistic_service
            .update_latencies(&shard_manager)
            .await;
    }
}

impl DiscordBot {
    pub async fn new(
        config: Arc<Config>,
        services: Arc<Services>,
        shutdown: Shutdown,
    ) -> Result<DiscordBot, Box<dyn Error>> {
        let slash_commands = discord::slash_commands::get_all_commands();

        let handler = DiscordEventHandler {
            config: config.clone(),
            services: services.clone(),
            slash_commands,
            message_processors: get_all_processors(),
        };

        let raw_handler = DiscordRawEventHandler {
            services: services.clone(),
        };

        let client = Client::builder(
            config.discord_token.parse()?,
            GatewayIntents::GUILDS
                | GatewayIntents::GUILD_MEMBERS
                | GatewayIntents::GUILD_MESSAGES
                | GatewayIntents::MESSAGE_CONTENT,
        )
        .event_handler(handler)
        .raw_event_handler(raw_handler)
        .await?;

        if let Some(guild_service) = services.get::<GuildService>() {
            guild_service.insert_http(client.http.clone()).await;
        }

        tokio::spawn(feed_shard_statistics(
            client.shard_manager.clone(),
            services.clone(),
            shutdown.clone(),
        ));

        run_scheduled_tasks(client.http.clone(), services.clone(), shutdown.clone());

        Ok(DiscordBot { client })
    }

    pub async fn connect(&mut self) -> Result<(), Arc<serenity::Error>> {
        self.client.start_autosharded().await.map_err(Arc::new)
    }
}

struct DiscordRawEventHandler {
    services: Arc<Services>,
}

#[async_trait]
impl RawEventHandler for DiscordRawEventHandler {
    async fn raw_event(&self, _ctx: Context, _ev: &Event) {
        if let Some(service) = self.services.get::<WatchdogService>() {
            service.feed().await;
        }
    }
}

struct DiscordEventHandler {
    config: Arc<Config>,
    slash_commands: SlashCommands,
    message_processors: MessageProcessors,
    services: Arc<Services>,
}

pub async fn initialize_slash_commands(
    http: &Http,
    slash_commands: &SlashCommands,
) -> Result<(), serenity::Error> {
    warn!("initializing slash commands");
    let _ = Command::set_global_commands(
        http,
        &slash_commands
            .0
            .values()
            .map(|slash_command| slash_command.create_command())
            .chain(
                [
                    CreateCommand::new("Report").kind(CommandType::Message),
                    CreateCommand::new("Format Code").kind(CommandType::Message),
                ]
                .into_iter(),
            )
            .collect::<Vec<_>>(),
    )
    .await?;
    warn!("initialized slash commands!");
    Ok(())
}

#[async_trait]
impl EventHandler for DiscordEventHandler {
    async fn channel_create(&self, _ctx: Context, new: GuildChannel) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_channels(new.guild_id)
                .await;
        }
    }

    async fn channel_delete(
        &self,
        _ctx: Context,
        new: GuildChannel,
        _messages: Option<VecDeque<Message>>,
    ) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_channels(new.guild_id)
                .await;
        }
    }

    async fn channel_update(&self, _ctx: Context, _old: Option<GuildChannel>, new: GuildChannel) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_channels(new.guild_id)
                .await;
        }
    }

    async fn guild_create(&self, _ctx: Context, guild: Guild, _is_new: Option<bool>) {
        if let Some(statistic_service) = self.services.get::<GuildStatisticService>() {
            statistic_service.add_guild(&guild).await;
        }
    }

    async fn guild_delete(
        &self,
        _ctx: Context,
        incomplete: UnavailableGuild,
        _full: Option<Guild>,
    ) {
        if let Some(statistic_service) = self.services.get::<GuildStatisticService>() {
            statistic_service.remove_guild(&incomplete).await;
        }

        if !incomplete.unavailable {
            if let Some(guild_service) = self.services.get::<GuildService>() {
                guild_service.invalidate_cached_guild(incomplete.id).await;
            }
        }
    }

    async fn guild_member_addition(&self, ctx: Context, new_member: Member) {
        let guild_id = new_member.guild_id;

        if let Some(statistic_service) = self.services.get::<GuildStatisticService>() {
            statistic_service
                .increment_guild_member_count(guild_id)
                .await;
        }

        let setting = if let Some(setting_service) = self.services.get::<SettingService>() {
            setting_service.get_setting(guild_id).await
        } else {
            return;
        };

        let guild = if let Some(guild_service) = self.services.get::<GuildService>() {
            match guild_service.get_guild(guild_id).await {
                Ok(guild) => guild,
                Err(_) => return,
            }
        } else {
            return;
        };

        if setting.invite_link_remover && new_member.user.name.contains("discord.gg/") {
            let _ = guild_id
                .kick(
                    &ctx.http,
                    new_member.user.id,
                    Some("Username contains invite link"),
                )
                .await
                .map_err(|err| {
                    error!("failed to issue discord kick {}", err);
                    err
                });
            return;
        }

        if setting.welcome_message {
            let content = setting
                .message
                .replace("$user", &format!("{}", new_member.mention()))
                .replace("$guild", &guild.name);

            let content = if setting.holding_room {
                content.replace("$minute", &setting.holding_room_minutes.to_string())
            } else {
                content
            };

            if let Some(id) = NonZeroU64::new(setting.welcome_message_channel_id as u64) {
                let channel_id = ChannelId::new(id.get());
                let message = CreateMessage::default().content(content);

                let _ = channel_id
                    .send_message(&ctx.http, message)
                    .await
                    .map_err(|err| {
                        error!("failed to send welcome message {}", err);
                        err
                    });
            }
        }

        if setting.holding_room {
            if let Some(join_service) = self.services.get::<JoinService>() {
                join_service
                    .issue_join(guild_id, new_member.user.id, setting.holding_room_minutes)
                    .await;
            }
        }

        if setting.join_captcha {
            if let Ok(dm_channel) = new_member.user.id.create_dm_channel(&ctx.http).await {
                let content = format!(
                    "Welcome to {}! To enter you must complete this captcha.\n{}/captcha/{}/{}",
                    &guild.name,
                    self.config.self_url,
                    guild_id.get(),
                    new_member.user.id.get()
                );
                let message = CreateMessage::default().content(content);

                let _ = dm_channel
                    .id
                    .send_message(&ctx.http, message)
                    .await
                    .map_err(|err| {
                        error!("failed to send captcha challenge DM {}", err);
                        err
                    });
            }
        }

        let mute_service = if let Some(service) = self.services.get::<MuteService>() {
            service
        } else {
            return;
        };

        let mute_records = mute_service
            .fetch_valid_mutes(guild_id, new_member.user.id)
            .await;

        if mute_records.is_empty() {
            return;
        }

        let role_id = match mute_service
            .fetch_muted_role_id(&ctx.http, &self.services, guild_id)
            .await
        {
            Ok(role) => role,
            Err(_) => {
                return;
            }
        };

        let _ = ctx
            .http
            .add_member_role(
                guild_id,
                new_member.user.id,
                role_id,
                Some("Preventing mute evasion"),
            )
            .await
            .map_err(|err| {
                error!("failed to issue discord member role add {}", err);
                err
            });
    }

    async fn guild_member_removal(
        &self,
        _ctx: Context,
        guild_id: GuildId,
        kicked: User,
        _member: Option<Member>,
    ) {
        if let Some(statistic_service) = self.services.get::<GuildStatisticService>() {
            statistic_service
                .decrement_guild_member_count(guild_id)
                .await;
        }

        if let Some(join_service) = self.services.get::<JoinService>() {
            join_service.delete_user_joins(guild_id, kicked.id).await;
        }

        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_member(guild_id, kicked.id)
                .await;
        }
    }

    // serenity merged no-cache and cached methods so ignore underscore prefixed parameters, they only exist for cache users.
    async fn guild_member_update(
        &self,
        _ctx: Context,
        _old: Option<Member>,
        _new: Option<Member>,
        new: GuildMemberUpdateEvent,
    ) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_member(new.guild_id, new.user.id)
                .await;
        }
    }

    async fn guild_role_create(&self, _ctx: Context, new: Role) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_roles(new.guild_id)
                .await;
        }
    }

    async fn guild_role_delete(
        &self,
        _ctx: Context,
        guild_id: GuildId,
        _removed_role_id: RoleId,
        _role: Option<Role>,
    ) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service.invalidate_cached_guild_roles(guild_id).await;
        }
    }

    async fn guild_role_update(&self, _ctx: Context, _old: Option<Role>, new: Role) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_roles(new.guild_id)
                .await;
        }
    }

    async fn guild_update(&self, _ctx: Context, _old: Option<Guild>, new: PartialGuild) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service.invalidate_cached_guild(new.id).await;
        }
    }

    async fn message(&self, ctx: Context, message: Message) {
        if message.author.bot() {
            return;
        }

        if message.webhook_id.is_some() {
            return;
        }

        if message.content.is_empty() {
            return;
        }

        if message.kind != MessageType::Regular && message.kind != MessageType::InlineReply {
            return;
        }

        let (guild_id, member) = if let Some(GuildMessageCreated { guild_id, member }) =
            verify_guild_message_create(&message)
        {
            (guild_id, member)
        } else {
            return;
        };

        let setting_service = if let Some(service) = self.services.get::<SettingService>() {
            service
        } else {
            return;
        };

        let setting = setting_service.get_setting(guild_id).await;

        let guild_service = if let Some(service) = self.services.get::<GuildService>() {
            service
        } else {
            return;
        };

        let permissions = if let Ok(permissions) = guild_service
            .get_permissions(message.author.id, &member.roles, guild_id)
            .await
        {
            permissions
        } else {
            return;
        };

        for (i, processor) in self.message_processors.0.iter().enumerate() {
            match processor
                .handle_message(
                    &ctx,
                    &message.content,
                    guild_id,
                    message.channel_id,
                    message.id,
                    &message.author,
                    permissions,
                    &setting,
                    &self.services,
                )
                .await
            {
                Ok(true) => break,
                Ok(false) => continue,
                Err(err) => {
                    error!("failed to run message processor id: {}, err: {}", i, err);
                    break;
                }
            }
        }
    }

    async fn message_update(
        &self,
        ctx: Context,
        _old: Option<Message>,
        _new: Option<Message>,
        message: MessageUpdateEvent,
    ) {
        let author = if let Some(author) = &message.author {
            author
        } else {
            return;
        };

        let (guild_id, content) = if let Some(GuildMessageUpdated { guild_id, content }) =
            verify_guild_message_update(&message)
        {
            (guild_id, content)
        } else {
            return;
        };

        if author.bot() {
            return;
        }

        if content.is_empty() {
            return;
        }

        let setting_service = if let Some(service) = self.services.get::<SettingService>() {
            service
        } else {
            return;
        };

        let setting = setting_service.get_setting(guild_id).await;

        let guild_service = if let Some(service) = self.services.get::<GuildService>() {
            service
        } else {
            return;
        };

        let member = if let Ok(member) = guild_service.get_member(guild_id, author.id).await {
            member
        } else {
            return;
        };

        let permissions = if let Ok(permissions) = guild_service
            .get_permissions(author.id, &member.roles, guild_id)
            .await
        {
            permissions
        } else {
            return;
        };

        for (i, processor) in self.message_processors.0.iter().enumerate() {
            match processor
                .handle_message(
                    &ctx,
                    content,
                    guild_id,
                    message.channel_id,
                    message.id,
                    author,
                    permissions,
                    &setting,
                    &self.services,
                )
                .await
            {
                Ok(true) => break,
                Ok(false) => continue,
                Err(err) => {
                    error!("failed to run message processor id: {}, err: {}", i, err);
                    break;
                }
            }
        }

        return;
    }

    async fn ready(&self, ctx: Context, data_about_bot: Ready) {
        let shard = data_about_bot
            .shard
            .map(|info| (info.id.0, info.total))
            .unwrap_or((0, NonZeroU16::new(1).unwrap()));
        // Watching over users. [0 / 1]
        let shard_status = format!("over users. [{} / {}]", shard.0, shard.1);
        let activity = ActivityData::watching(shard_status);
        ctx.set_activity(Some(activity));
    }

    async fn user_update(&self, _ctx: Context, _old: Option<CurrentUser>, new: CurrentUser) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service.invalidate_cached_user(new.id).await;
        }
    }

    async fn interaction_create(&self, ctx: Context, interaction: Interaction) {
        if let Interaction::Autocomplete(autocomplete_interaction) = interaction {
            // only tag command has autocomplete so no need to filter based on command name
            if let Some(tag_service) = self.services.get::<TagService>() {
                let guild_id = if let Some(id) = autocomplete_interaction.guild_id {
                    id
                } else {
                    return;
                };

                let tags = tag_service.get_tag_names(guild_id).await;

                let tag_option =
                    if let Some(s) = autocomplete_interaction.data.string_autocomplete("name") {
                        s
                    } else {
                        return;
                    };

                let tag_choices = if tag_option.is_empty() {
                    tags
                } else {
                    let mut engine = SimSearch::new_with(SearchOptions::new().threshold(0.75));

                    for (i, tag_name) in tags.iter().enumerate() {
                        engine.insert(i, tag_name);
                    }

                    let results = engine.search(tag_option);

                    results.into_iter().map(|i| tags[i].clone()).collect()
                };

                let response = CreateAutocompleteResponse::default().set_choices(
                    tag_choices
                        .iter()
                        .take(25)
                        .map(|tag| AutocompleteChoice::new(tag.clone(), tag.as_str()))
                        .collect::<Vec<_>>(),
                );

                let builder = CreateInteractionResponse::Autocomplete(response);

                let _ = autocomplete_interaction
                    .create_response(&ctx.http, builder)
                    .await
                    .map_err(|err| {
                        error!("failed to create autocomplete response {}", err);
                        err
                    });
            }

            return;
        }

        if let Interaction::Command(command) = interaction {
            // TODO(sam): maybe remove this check later and rely on dm_permission field of command
            let (guild_id, member) = match (command.guild_id, &command.member) {
                (Some(guild_id), Some(member)) => (guild_id, member.as_ref()),
                _ => {
                    let _ = reply_to_interaction_str(
                        &ctx.http,
                        &command,
                        "Jim's commands can only be used in servers.",
                        true,
                    )
                    .await;
                    return;
                }
            };

            match command.data.kind {
                CommandType::ChatInput => {
                    let name = &command.data.name;
                    let slash_command_handler = self.slash_commands.0.get(name.as_str());
                    if let Some(slash_command_handler) = slash_command_handler {
                        if let Err(err) = slash_command_handler
                            .handle_command(&ctx, &command, &self.config, self.services.as_ref())
                            .await
                        {
                            error!(
                                name = name.as_str(),
                                "failed to handle slash command {}", err
                            );
                        }
                    }
                }
                CommandType::Message => {
                    let name = &command.data.name;
                    let target_message = match command.data.resolved.messages.iter().next() {
                        Some(message) => message,
                        None => {
                            error!(
                                "received a message command without targeted message {:?}",
                                &command
                            );
                            return;
                        }
                    };

                    if name == "Report" {
                        let setting = if let Some(service) = self.services.get::<SettingService>() {
                            service.get_setting(guild_id).await
                        } else {
                            return;
                        };

                        if !setting.mod_log {
                            reply_to_interaction_str(
                                &ctx.http,
                                &command,
                                "This server doesn't have reporting enabled!",
                                true,
                            )
                            .await;
                            return;
                        }

                        let embed = CreateEmbed::default()
                            .color(Color::new(0xFF2900))
                            .timestamp(target_message.timestamp)
                            .title("Message Report")
                            .field("Reporter:", member.user.tag_and_id(), false)
                            .field("Reported:", target_message.author.tag_and_id(), false)
                            .field(
                                "Message Link:",
                                target_message
                                    .id
                                    .link(target_message.channel_id, Some(guild_id)),
                                false,
                            )
                            .footer(CreateEmbedFooter::new("Message sent on"));

                        let message = CreateMessage::default().add_embed(embed);

                        let report_channel_id =
                            if let Some(id) = NonZeroU64::new(setting.report_channel_id as u64) {
                                ChannelId::new(id.get())
                            } else {
                                reply_to_interaction_str(
                                    &ctx.http,
                                    &command,
                                    "This server doesn't have reporting enabled!",
                                    true,
                                )
                                .await;
                                return;
                            };

                        let _ = report_channel_id.send_message(&ctx.http, message).await;
                        reply_to_interaction_str(&ctx.http, &command, "Reported.", true).await;
                    } else if name == "Format Code" {
                        if target_message.content.is_empty() {
                            reply_to_interaction_str(
                                &ctx.http,
                                &command,
                                "Message has no content.",
                                true,
                            )
                            .await;
                            return;
                        }

                        if target_message.content.contains("```") {
                            reply_to_interaction_str(
                                &ctx.http,
                                &command,
                                "Message already contains a code block.",
                                true,
                            )
                            .await;
                            return;
                        }

                        if let Err(err) = defer_interaction(&ctx.http, &command).await {
                            error!("failed to defer an interaction {:?} {:?}", err, &command);
                            return;
                        }

                        let language_select = EditInteractionResponse::new().components(vec![
                            CreateActionRow::SelectMenu(
                                CreateSelectMenu::new(
                                    "language_select",
                                    CreateSelectMenuKind::String {
                                        options: PROGRAMMING_LANGUAGES
                                            .iter()
                                            .map(|(label, value)| {
                                                CreateSelectMenuOption::new(*label, *value)
                                            })
                                            .collect(),
                                    },
                                )
                                .placeholder("No language selected"),
                            ),
                        ]);

                        let language_select_message =
                            match command.edit_response(&ctx.http, language_select).await {
                                Ok(message) => message,
                                Err(_) => {
                                    edit_deferred_interaction_response(
                                        &ctx.http,
                                        &command,
                                        &format!("```\n{}\n```", &target_message.content),
                                    )
                                    .await;
                                    return;
                                }
                            };

                        let component_interaction =
                            match ComponentInteractionCollector::new(ctx.shard.clone())
                                .timeout(Duration::from_secs(10))
                                .message_id(language_select_message.id)
                                .author_id(command.user.id)
                                .next()
                                .await
                            {
                                Some(interaction) => interaction,
                                None => {
                                    edit_deferred_interaction_response(
                                        &ctx.http,
                                        &command,
                                        &format!("```\n{}\n```", &target_message.content),
                                    )
                                    .await;
                                    return;
                                }
                            };

                        let selected_language = match &component_interaction.data.kind {
                            ComponentInteractionDataKind::StringSelect { values } => {
                                match values.first().map(|value| value.as_str()) {
                                    Some("None") => "",
                                    Some(value) => value,
                                    None => {
                                        error!("no string select value received from component interaction {:?}", &component_interaction);
                                        return;
                                    }
                                }
                            }
                            _ => {
                                error!("different component interaction data was received than expected {:?}", &component_interaction);
                                return;
                            }
                        };

                        let formatted_content =
                            format!("```{}\n{}\n```", selected_language, &target_message.content);

                        let _ = component_interaction
                            .create_response(
                                &ctx.http,
                                CreateInteractionResponse::UpdateMessage(
                                    CreateInteractionResponseMessage::new()
                                        .content(&formatted_content)
                                        .components(vec![]),
                                ),
                            )
                            .await;
                    } else {
                        // we only have two message commands
                        error!(
                            "received a message command with unknown name {:?}",
                            &command
                        );
                        return;
                    }
                }
                _ => {
                    error!("received an unknown command {:?}", &command);
                }
            }
        }
    }
}
