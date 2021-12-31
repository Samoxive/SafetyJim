use std::error::Error;
use std::process::exit;
use std::sync::Arc;

use serenity::async_trait;
use serenity::client::bridge::gateway::ShardManager;
use serenity::client::EventHandler;
use serenity::model::channel::{Channel, GuildChannel, Message, MessageType};
use serenity::model::event::GuildMemberUpdateEvent;
use serenity::model::gateway::{Activity, GatewayIntents};
use serenity::model::guild::{Guild, GuildUnavailable, Member, PartialGuild, Role};
use serenity::model::id::{ChannelId, GuildId, RoleId};
use serenity::model::prelude::application_command::ApplicationCommand;
use serenity::model::prelude::{CurrentUser, Interaction, Ready, User};
use serenity::prelude::{Context, Mentionable};
use serenity::Client;
use tokio::sync::Mutex;
use tracing::error;
use typemap_rev::TypeMap;

use crate::config::Config;
use crate::discord;
use crate::discord::message_processors::{get_all_processors, MessageProcessors};
use crate::discord::scheduled::run_scheduled_tasks;
use crate::discord::slash_commands::SlashCommands;
use crate::discord::util::{invisible_success_reply, verify_guild_message, GuildMessage};
use crate::flags::Flags;
use crate::service::guild::GuildService;
use crate::service::guild_statistic::GuildStatisticService;
use crate::service::join::JoinService;
use crate::service::mute::MuteService;
use crate::service::setting::SettingService;
use crate::service::shard_statistic::ShardStatisticService;
use crate::service::tag::TagService;
use tokio::time::Duration;

const SHARD_LATENCY_POLLING_INTERVAL: u64 = 60;

pub struct DiscordBot {
    client: Client,
}

async fn feed_shard_statistics(shard_manager: Arc<Mutex<ShardManager>>, services: Arc<TypeMap>) {
    let shard_statistic_service =
        if let Some(shard_statistic_service) = services.get::<ShardStatisticService>() {
            shard_statistic_service
        } else {
            return;
        };

    loop {
        tokio::time::sleep(Duration::from_secs(SHARD_LATENCY_POLLING_INTERVAL)).await;
        shard_statistic_service
            .update_latencies(&*shard_manager.lock().await)
            .await;
    }
}

impl DiscordBot {
    pub async fn new(
        config: Arc<Config>,
        flags: Arc<Flags>,
        services: Arc<TypeMap>,
    ) -> Result<DiscordBot, Box<dyn Error>> {
        let slash_commands = discord::slash_commands::get_all_commands();

        let handler = DiscordEventHandler {
            create_slash_commands: flags.create_slash_commands,
            config: config.clone(),
            services: services.clone(),
            slash_commands,
            message_processors: get_all_processors(),
        };

        let application_id: u64 = config.oauth_client_id.parse()?;
        let client = Client::builder(&(config.discord_token))
            .event_handler(handler)
            .intents(
                GatewayIntents::GUILDS
                    | GatewayIntents::GUILD_MEMBERS
                    | GatewayIntents::GUILD_MESSAGES,
            )
            .application_id(application_id)
            .await?;

        if let Some(guild_service) = services.get::<GuildService>() {
            guild_service
                .insert_http(client.cache_and_http.http.clone())
                .await;
        }

        tokio::spawn(feed_shard_statistics(
            client.shard_manager.clone(),
            services.clone(),
        ));

        run_scheduled_tasks(client.cache_and_http.http.clone(), services.clone());

        Ok(DiscordBot { client })
    }

    pub async fn connect(&mut self) -> Result<(), Arc<serenity::Error>> {
        Ok(self.client.start_autosharded().await.map_err(Arc::new)?)
    }
}

struct DiscordEventHandler {
    create_slash_commands: bool,
    config: Arc<Config>,
    slash_commands: SlashCommands,
    message_processors: MessageProcessors,
    services: Arc<TypeMap>,
}

async fn initialize_slash_commands(
    ctx: &Context,
    slash_commands: &SlashCommands,
) -> Result<(), serenity::Error> {
    ApplicationCommand::set_global_application_commands(&ctx.http, |commands| {
        for (&_name, slash_command) in &slash_commands.0 {
            commands
                .create_application_command(move |command| slash_command.create_command(command));
        }
        commands
    })
    .await
    .map(|_| ())
}

#[async_trait]
impl EventHandler for DiscordEventHandler {
    async fn guild_create(&self, _ctx: Context, guild: Guild) {
        if let Some(statistic_service) = self.services.get::<GuildStatisticService>() {
            statistic_service.add_guild(&guild).await;
        }
    }

    async fn guild_update(&self, _ctx: Context, new: PartialGuild) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service.invalidate_cached_guild(new.id).await;
        }
    }

    async fn guild_delete(&self, _ctx: Context, incomplete: GuildUnavailable) {
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
                .kick_with_reason(
                    &*ctx.http,
                    new_member.user.id,
                    "Username contains invite link",
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
                .replace("$user", &format!("{:?}", new_member.mention()))
                .replace("$guild", &guild.name);

            let content = if setting.holding_room {
                content.replace("$minute", &setting.holding_room_minutes.to_string())
            } else {
                content
            };

            let channel_id = ChannelId(setting.welcome_message_channel_id as u64);
            let _ = channel_id
                .send_message(&*ctx.http, |message| message.content(content))
                .await
                .map_err(|err| {
                    error!("failed to send welcome message {}", err);
                    err
                });
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
                let _ = dm_channel
                    .send_message(&ctx.http, |message| {
                        message.content(format!("Welcome to {}! To enter you must complete this captcha.\n{}/captcha/{}/{}", &guild.name, self.config.self_url, guild_id.0, new_member.user.id.0))
                    })
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

        let role = match mute_service
            .fetch_muted_role(&ctx.http, &*self.services, guild_id)
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
                guild_id.0,
                new_member.user.id.0,
                role.0,
                Some("Preventing mute evasion"),
            )
            .await
            .map_err(|err| {
                error!("failed to issue discord member role add {}", err);
                err
            });
    }

    async fn guild_member_update(&self, _ctx: Context, new: GuildMemberUpdateEvent) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_member(new.guild_id, new.user.id)
                .await;
        }
    }

    async fn guild_member_removal(&self, _ctx: Context, guild_id: GuildId, kicked: User) {
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

    async fn ready(&self, ctx: Context, data_about_bot: Ready) {
        let shard = data_about_bot.shard.unwrap_or([0, 1]);
        // Watching over users. [0 / 1]
        let shard_status = format!("over users. [{} / {}]", shard[0], shard[1]);
        let activity = Activity::watching(shard_status);
        ctx.set_activity(activity).await;

        if self.create_slash_commands {
            if let Err(err) = initialize_slash_commands(&ctx, &self.slash_commands).await {
                error!("failed to create slash commands {}", err);
                exit(-1);
            }
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
                let _ = autocomplete_interaction
                    .create_autocomplete_response(&*ctx.http, |response| {
                        for tag in tags {
                            response.add_string_choice(&tag, &tag);
                        }

                        response
                    })
                    .await
                    .map_err(|err| {
                        error!("failed to create autocomplete response {}", err);
                        err
                    });
            }

            return;
        }

        if let Interaction::ApplicationCommand(command) = interaction {
            if command.guild_id.is_none() || command.member.is_none() {
                invisible_success_reply(
                    &*ctx.http,
                    &command,
                    "Jim's commands can only be used in servers.",
                )
                .await;
                return;
            }

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
    }

    async fn message(&self, ctx: Context, message: Message) {
        if message.author.bot {
            return;
        }

        if message.webhook_id.is_some() {
            return;
        }

        if message.content.is_empty() {
            return;
        }

        if message.kind != MessageType::Regular || message.kind != MessageType::InlineReply {
            return;
        }

        let (guild_id, member) =
            if let Ok(GuildMessage { guild_id, member }) = verify_guild_message(&message) {
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

        let permissions =
            if let Ok(permissions) = guild_service.get_permissions(&member.roles, guild_id).await {
                permissions
            } else {
                return;
            };

        for (i, processor) in self.message_processors.0.iter().enumerate() {
            match processor
                .handle_message(
                    &ctx,
                    &message,
                    guild_id,
                    member,
                    permissions,
                    &*setting,
                    &*self.config,
                    &*self.services,
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

    async fn guild_role_create(&self, _ctx: Context, new: Role) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_roles(new.guild_id)
                .await;
        }
    }

    async fn guild_role_update(&self, _ctx: Context, new: Role) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_roles(new.guild_id)
                .await;
        }
    }

    async fn guild_role_delete(&self, _ctx: Context, guild_id: GuildId, _removed_role_id: RoleId) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service.invalidate_cached_guild_roles(guild_id).await;
        }
    }

    async fn user_update(&self, _ctx: Context, new: CurrentUser) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service.invalidate_cached_user(new.id).await;
        }
    }

    async fn channel_create(&self, _ctx: Context, new: &GuildChannel) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_channels(new.guild_id)
                .await;
        }
    }

    async fn channel_update(&self, _ctx: Context, new: Channel) {
        if let Some(guild_channel) = new.guild() {
            if let Some(guild_service) = self.services.get::<GuildService>() {
                guild_service
                    .invalidate_cached_guild_channels(guild_channel.guild_id)
                    .await;
            }
        }
    }

    async fn channel_delete(&self, _ctx: Context, new: &GuildChannel) {
        if let Some(guild_service) = self.services.get::<GuildService>() {
            guild_service
                .invalidate_cached_guild_channels(new.guild_id)
                .await;
        }
    }
}
