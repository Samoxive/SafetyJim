use std::collections::HashMap;
use std::time::Duration;

use anyhow::bail;
use async_recursion::async_recursion;
use serenity::all::{
    CommandData, CommandDataOptionValue, CommandInteraction, CreateAllowedMentions, CreateEmbed,
    CreateInteractionResponse, CreateInteractionResponseMessage, EditInteractionResponse, Error,
    GenericChannelId, GuildId, Http, Member, Message, MessageFlags, MessageId, MessageUpdateEvent,
    PartialMember, Permissions, Role, RoleId, User, UserId,
};
// PartialChannel is no longer available in the updated serenity API
use tracing::{error, warn};

use crate::database::settings::{
    Setting, ACTION_BAN, ACTION_HARDBAN, ACTION_KICK, ACTION_MUTE, ACTION_SOFTBAN, ACTION_WARN,
};
use crate::service::ban::BanService;
use crate::service::guild::CachedRole;
use crate::service::hardban::HardbanService;
use crate::service::kick::KickService;
use crate::service::mute::MuteService;
use crate::service::softban::SoftbanService;
use crate::service::warn::WarnService;
use crate::service::Services;
use crate::util::now;

pub mod mod_log;
pub mod user_dm;

pub trait CommandDataExt {
    fn option(&self, option_name: &str) -> Option<&CommandDataOptionValue>;
    fn string(&self, option_name: &str) -> Option<&str>;
    fn integer(&self, option_name: &str) -> Option<i64>;
    fn boolean(&self, option_name: &str) -> Option<bool>;
    fn user(&self, option_name: &str) -> Option<(&User, Option<&PartialMember>)>;
    // PartialChannel is no longer available in the updated serenity API
    // fn channel(&self, option_name: &str) -> Option<&PartialChannel>;
    fn role(&self, option_name: &str) -> Option<&Role>;
    fn number(&self, option_name: &str) -> Option<f64>;
    fn string_autocomplete(&self, option_name: &str) -> Option<&str>;
}

impl CommandDataExt for CommandData {
    fn option(&self, option_name: &str) -> Option<&CommandDataOptionValue> {
        self.options
            .iter()
            .find(|option| option.name == option_name)
            .map(|option| &option.value)
    }

    fn string(&self, option_name: &str) -> Option<&str> {
        if let Some(CommandDataOptionValue::String(str)) = self.option(option_name) {
            Some(str.as_str())
        } else {
            None
        }
    }

    fn integer(&self, option_name: &str) -> Option<i64> {
        if let Some(CommandDataOptionValue::Integer(i)) = self.option(option_name) {
            Some(*i)
        } else {
            None
        }
    }

    fn boolean(&self, option_name: &str) -> Option<bool> {
        if let Some(CommandDataOptionValue::Boolean(b)) = self.option(option_name) {
            Some(*b)
        } else {
            None
        }
    }

    fn user(&self, option_name: &str) -> Option<(&User, Option<&PartialMember>)> {
        if let Some(CommandDataOptionValue::User(user_id)) = self.option(option_name) {
            let user = self.resolved.users.get(user_id)?;
            let member = self.resolved.members.get(user_id);

            Some((user, member))
        } else {
            None
        }
    }

    // PartialChannel is no longer available in the updated serenity API
    // fn channel(&self, option_name: &str) -> Option<&PartialChannel> {
    //     if let Some(CommandDataOptionValue::Channel(channel_id)) = self.option(option_name) {
    //         let channel = self.resolved.channels.get(channel_id)?;
    //
    //         Some(channel)
    //     } else {
    //         None
    //     }
    // }

    fn role(&self, option_name: &str) -> Option<&Role> {
        if let Some(CommandDataOptionValue::Role(role_id)) = self.option(option_name) {
            let role = self.resolved.roles.get(role_id)?;

            Some(role)
        } else {
            None
        }
    }

    fn number(&self, option_name: &str) -> Option<f64> {
        if let Some(CommandDataOptionValue::Number(n)) = self.option(option_name) {
            Some(*n)
        } else {
            None
        }
    }

    fn string_autocomplete(&self, option_name: &str) -> Option<&str> {
        if let Some(autocomplete_option) = self.autocomplete() {
            if autocomplete_option.name == option_name {
                return Some(autocomplete_option.value);
            }
        }

        None
    }
}

pub fn is_staff(permissions: Permissions) -> bool {
    permissions.administrator()
        || permissions.ban_members()
        || permissions.kick_members()
        || permissions.manage_roles()
        || permissions.manage_messages()
}

pub fn get_permissions(
    member_id: UserId,
    member_roles: &[RoleId],
    guild_id: GuildId,
    guild_roles: &HashMap<RoleId, CachedRole>,
    guild_owner_id: UserId,
) -> Option<Permissions> {
    if member_id == guild_owner_id {
        return Some(Permissions::ADMINISTRATOR);
    }

    let everyone = match guild_roles.get(&RoleId::new(guild_id.get())) {
        Some(everyone) => everyone,
        None => {
            return None;
        }
    };

    let mut permissions = everyone.permissions;

    for role in member_roles {
        if let Some(role) = guild_roles.get(role) {
            if role.permissions.contains(Permissions::ADMINISTRATOR) {
                return Some(Permissions::ADMINISTRATOR);
            }

            permissions |= role.permissions;
        } else {
            return None;
        }
    }

    Some(permissions)
}

pub trait UserExt {
    fn tag_and_id(&self) -> String;
}

impl UserExt for User {
    fn tag_and_id(&self) -> String {
        format!("{} ({})", self.tag(), self.id.get())
    }
}

pub struct GuildSlashCommandInteraction<'a> {
    pub guild_id: GuildId,
    pub member: &'a Member,
    pub permissions: Permissions,
}

pub fn verify_guild_slash_command(
    interaction: &CommandInteraction,
) -> anyhow::Result<GuildSlashCommandInteraction<'_>> {
    let member = if let Some(member) = &interaction.member {
        member
    } else {
        bail!("interaction has no member field");
    };

    let permissions = if let Some(permissions) = member.permissions {
        permissions
    } else {
        bail!("interaction's member has no permissions field");
    };

    let guild_id = if let Some(guild_id) = interaction.guild_id {
        guild_id
    } else {
        bail!("interaction has missing guild id");
    };

    Ok(GuildSlashCommandInteraction {
        guild_id,
        member,
        permissions,
    })
}

pub struct GuildMessageCreated<'a> {
    pub guild_id: GuildId,
    pub member: &'a PartialMember,
}

pub fn verify_guild_message_create(message: &Message) -> Option<GuildMessageCreated> {
    Some(GuildMessageCreated {
        guild_id: message.guild_id?,
        member: message.member.as_ref()?,
    })
}

pub struct GuildMessageUpdated<'a> {
    pub guild_id: GuildId,
    pub content: &'a str,
}

pub fn verify_guild_message_update(
    message: &MessageUpdateEvent,
) -> Option<GuildMessageUpdated<'_>> {
    // In the updated serenity API, the guild_id and content fields are nested under the message field
    let guild_id = message.message.guild_id?;
    let content = message.message.content.as_str();

    Some(GuildMessageUpdated { guild_id, content })
}

fn create_interaction_response_message<'a>(
    is_ephemeral: bool,
) -> CreateInteractionResponseMessage<'a> {
    if is_ephemeral {
        CreateInteractionResponseMessage::new().flags(MessageFlags::EPHEMERAL)
    } else {
        CreateInteractionResponseMessage::new()
    }
}

// function doesn't return a Result because we never care if our reply succeeds, replies to interactions rarely fail
// and when they do it's usually because we missed the 3 seconds reply window, in which case Jim is probably overloaded
// and can't respond to interactions in time.
async fn reply_to_interaction(
    http: &Http,
    interaction: &CommandInteraction,
    response_data: CreateInteractionResponseMessage<'_>,
) {
    let interaction_response = CreateInteractionResponse::Message(response_data);

    let _ = interaction
        .create_response(http, interaction_response)
        .await
        .map_err(|err| {
            error!("failed to reply to interaction {:?} {}", interaction, err);
            err
        });
}

pub async fn reply_to_interaction_str(
    http: &Http,
    interaction: &CommandInteraction,
    content: &str,
    is_ephemeral: bool,
) {
    let response_data =
        create_interaction_response_message(is_ephemeral).content(content.to_string());

    reply_to_interaction(http, interaction, response_data).await;
}

pub async fn reply_to_interaction_embed(
    http: &Http,
    interaction: &CommandInteraction,
    embed: CreateEmbed<'_>,
    is_ephemeral: bool,
) {
    let response_data = create_interaction_response_message(is_ephemeral).add_embed(embed);

    reply_to_interaction(http, interaction, response_data).await;
}

pub async fn defer_interaction(http: &Http, interaction: &CommandInteraction) -> Result<(), Error> {
    let interaction_response =
        CreateInteractionResponse::Defer(CreateInteractionResponseMessage::new());

    interaction
        .create_response(http, interaction_response)
        .await?;

    Ok(())
}

pub async fn edit_deferred_interaction_response(
    http: &Http,
    interaction: &CommandInteraction,
    content: &str,
) {
    let builder = EditInteractionResponse::default()
        .content(content)
        .allowed_mentions(CreateAllowedMentions::new())
        .components(vec![]);

    let _ = interaction
        .edit_response(http, builder)
        .await
        .map_err(|err| {
            error!("failed to edit interaction reply {:?} {}", interaction, err);
            err
        });
}

pub async fn unauthorized_reply(
    http: &Http,
    interaction: &CommandInteraction,
    required_permission: Permissions,
) {
    reply_to_interaction_str(
        http,
        interaction,
        &format!(
            "You don't have enough permissions to execute this command! Required permission: {}",
            required_permission
        ),
        true,
    )
    .await;
}

pub enum CleanMessagesFailure {
    Unauthorized,
    Other,
}

pub async fn clean_messages(
    http: &Http,
    channel: GenericChannelId,
    messages: Vec<MessageId>,
    mod_user: &User,
) -> Result<(), CleanMessagesFailure> {
    let mut old_messages = vec![];
    let mut new_messages = vec![];
    let now = now();

    for message in messages.into_iter() {
        let timestamp = message.created_at().unix_timestamp() as u64;
        if (now - timestamp) <= 60 * 60 * 24 * 12 {
            new_messages.push(message);
        } else {
            old_messages.push(message);
        }
    }

    let reason = format!("Clean operation initiated by mod {}", mod_user.tag_and_id());
    let result = if new_messages.len() >= 2 {
        // In the updated serenity API, we need to use the Http client directly
        http.delete_messages(channel, &new_messages, Some(&reason))
            .await
    } else if new_messages.len() == 1 {
        // In the updated serenity API, we need to use the Http client directly
        http.delete_message(channel, new_messages[0], Some(&reason))
            .await
    } else {
        Ok(())
    };

    if let Err(err) = result {
        return match err.discord_error_code() {
            Some(50013) => Err(CleanMessagesFailure::Unauthorized),
            _ => {
                error!("failed to delete messages in bulk {}", err);
                Err(CleanMessagesFailure::Other)
            }
        };
    }

    let mut result = Ok(());
    for message_id in old_messages {
        if let Err(err) = http
            .delete_message(channel, message_id, Some(&reason))
            .await
        {
            result = Err(err);
            break;
        }
    }

    if let Err(err) = result {
        return match err.discord_error_code() {
            Some(50013) => Err(CleanMessagesFailure::Unauthorized),
            _ => {
                error!("failed to delete messages one by one {}", err);
                Err(CleanMessagesFailure::Other)
            }
        };
    }

    Ok(())
}

pub trait SerenityErrorExt {
    fn discord_error_code(&self) -> Option<u32>;
}

impl SerenityErrorExt for Error {
    fn discord_error_code(&self) -> Option<u32> {
        match self {
            Error::Http(http_error) => {
                // In the updated serenity API, the error code is accessed differently
                // Try to extract the error code from the HttpError directly
                http_error.status_code().and_then(|status| {
                    if status.as_u16() == 403 {
                        Some(50013) // Missing Permissions
                    } else {
                        None
                    }
                })
            }
            _ => None,
        }
    }
}

#[async_recursion]
pub async fn execute_mod_action(
    action_kind: i32,
    http: &Http,
    guild_id: GuildId,
    guild_name: &str,
    setting: &Setting,
    services: &Services,
    channel_id: Option<GenericChannelId>,
    mod_user_id: UserId,
    mod_user_tag_and_id: &str,
    target_user: &User,
    reason: String,
    duration: Option<Duration>,
    call_depth: i32,
) {
    if call_depth >= 3 {
        return;
    }

    let warn_service = if let Some(service) = services.get::<WarnService>() {
        service
    } else {
        error!("couldn't get warn service!");
        return;
    };

    let mute_service = if let Some(service) = services.get::<MuteService>() {
        service
    } else {
        error!("couldn't get mute service!");
        return;
    };

    let kick_service = if let Some(service) = services.get::<KickService>() {
        service
    } else {
        error!("couldn't get kick service!");
        return;
    };

    let ban_service = if let Some(service) = services.get::<BanService>() {
        service
    } else {
        error!("couldn't get ban service!");
        return;
    };

    let softban_service = if let Some(service) = services.get::<SoftbanService>() {
        service
    } else {
        error!("couldn't get softban service!");
        return;
    };

    let hardban_service = if let Some(service) = services.get::<HardbanService>() {
        service
    } else {
        error!("couldn't get hardban service!");
        return;
    };

    if action_kind == ACTION_WARN {
        let _ = warn_service
            .issue_warn(
                http,
                guild_id,
                guild_name,
                setting,
                services,
                channel_id,
                mod_user_id,
                mod_user_tag_and_id,
                target_user,
                reason,
                call_depth + 1,
            )
            .await;
    } else if action_kind == ACTION_MUTE {
        let _ = mute_service
            .issue_mute(
                http,
                guild_id,
                guild_name,
                setting,
                services,
                channel_id,
                mod_user_id,
                mod_user_tag_and_id,
                target_user,
                reason,
                duration,
                call_depth + 1,
            )
            .await;
    } else if action_kind == ACTION_KICK {
        let _ = kick_service
            .issue_kick(
                http,
                guild_id,
                guild_name,
                setting,
                services,
                channel_id,
                mod_user_id,
                mod_user_tag_and_id,
                target_user,
                reason,
                call_depth + 1,
            )
            .await;
    } else if action_kind == ACTION_BAN {
        let _ = ban_service
            .issue_ban(
                http,
                guild_id,
                guild_name,
                setting,
                channel_id,
                mod_user_id,
                mod_user_tag_and_id,
                target_user,
                reason,
                duration,
            )
            .await;
    } else if action_kind == ACTION_SOFTBAN {
        let _ = softban_service
            .issue_softban(
                http,
                guild_id,
                guild_name,
                setting,
                services,
                channel_id,
                mod_user_id,
                mod_user_tag_and_id,
                target_user,
                reason,
                1,
                call_depth + 1,
            )
            .await;
    } else if action_kind == ACTION_HARDBAN {
        let _ = hardban_service
            .issue_hardban(
                http,
                guild_id,
                guild_name,
                setting,
                channel_id,
                mod_user_id,
                mod_user_tag_and_id,
                target_user,
                reason,
            )
            .await;
    } else {
        warn!(
            action_kind = action_kind,
            guild_id = guild_id.get(),
            "invalid state for mod action duration type!"
        );
    }
}
