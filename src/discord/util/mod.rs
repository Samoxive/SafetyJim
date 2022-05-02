pub mod mod_log;
pub mod user_dm;

use anyhow::bail;
use serenity::http::{Http, HttpError};
use serenity::model::channel::{Message, PartialChannel};
use serenity::model::guild::{Member, PartialMember, Role};
use serenity::model::id::{ChannelId, GuildId, MessageId, RoleId, UserId};
use serenity::model::interactions::application_command::{
    ApplicationCommandInteraction, ApplicationCommandInteractionData,
    ApplicationCommandInteractionDataOptionValue,
};
use serenity::model::interactions::{
    InteractionApplicationCommandCallbackDataFlags, InteractionResponseType,
};
use serenity::model::user::User;
use serenity::model::Permissions;
use serenity::Error;
use std::collections::HashMap;

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
use crate::util::now;
use async_recursion::async_recursion;
use serenity::model::event::MessageUpdateEvent;
use std::time::Duration;
use tracing::{error, warn};
use typemap_rev::TypeMap;

pub trait ApplicationCommandInteractionDataExt {
    fn option(&self, option_name: &str) -> Option<&ApplicationCommandInteractionDataOptionValue>;
    fn string(&self, option_name: &str) -> Option<&str>;
    fn integer(&self, option_name: &str) -> Option<i64>;
    fn boolean(&self, option_name: &str) -> Option<bool>;
    fn user(&self, option_name: &str) -> Option<(&User, Option<&PartialMember>)>;
    fn channel(&self, option_name: &str) -> Option<&PartialChannel>;
    fn role(&self, option_name: &str) -> Option<&Role>;
    fn number(&self, option_name: &str) -> Option<f64>;
}

impl ApplicationCommandInteractionDataExt for ApplicationCommandInteractionData {
    fn option(&self, option_name: &str) -> Option<&ApplicationCommandInteractionDataOptionValue> {
        self.options
            .iter()
            .find(|option| option.name == option_name)
            .and_then(|option| option.resolved.as_ref())
    }

    fn string(&self, option_name: &str) -> Option<&str> {
        if let Some(ApplicationCommandInteractionDataOptionValue::String(str)) =
            self.option(option_name)
        {
            Some(str.as_str())
        } else {
            None
        }
    }

    fn integer(&self, option_name: &str) -> Option<i64> {
        if let Some(ApplicationCommandInteractionDataOptionValue::Integer(i)) =
            self.option(option_name)
        {
            Some(*i)
        } else {
            None
        }
    }

    fn boolean(&self, option_name: &str) -> Option<bool> {
        if let Some(ApplicationCommandInteractionDataOptionValue::Boolean(b)) =
            self.option(option_name)
        {
            Some(*b)
        } else {
            None
        }
    }

    fn user(&self, option_name: &str) -> Option<(&User, Option<&PartialMember>)> {
        if let Some(ApplicationCommandInteractionDataOptionValue::User(user, member)) =
            self.option(option_name)
        {
            Some((user, member.as_ref()))
        } else {
            None
        }
    }

    fn channel(&self, option_name: &str) -> Option<&PartialChannel> {
        if let Some(ApplicationCommandInteractionDataOptionValue::Channel(channel)) =
            self.option(option_name)
        {
            Some(channel)
        } else {
            None
        }
    }

    fn role(&self, option_name: &str) -> Option<&Role> {
        if let Some(ApplicationCommandInteractionDataOptionValue::Role(role)) =
            self.option(option_name)
        {
            Some(role)
        } else {
            None
        }
    }

    fn number(&self, option_name: &str) -> Option<f64> {
        if let Some(ApplicationCommandInteractionDataOptionValue::Number(n)) =
            self.option(option_name)
        {
            Some(*n)
        } else {
            None
        }
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

    let everyone = match guild_roles.get(&RoleId(guild_id.0)) {
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
        format!("{} ({})", self.tag(), self.id.0)
    }
}

pub struct GuildSlashCommandInteraction<'a> {
    pub guild_id: GuildId,
    pub member: &'a Member,
    pub permissions: Permissions,
}

pub fn verify_guild_slash_command(
    interaction: &ApplicationCommandInteraction,
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

pub fn verify_guild_message_update(message: &MessageUpdateEvent) -> Option<GuildMessageUpdated> {
    Some(GuildMessageUpdated {
        guild_id: message.guild_id?,
        content: message.content.as_ref()?,
    })
}

pub async fn reply_with_str(
    http: &Http,
    interaction: &ApplicationCommandInteraction,
    content: &str,
) {
    let _ = interaction
        .create_interaction_response(http, |response| {
            response
                .kind(InteractionResponseType::ChannelMessageWithSource)
                .interaction_response_data(|message| message.content(content))
        })
        .await
        .map_err(|err| {
            error!("failed to reply to interaction {:?} {}", interaction, err);
            err
        });
}

async fn invisible_reply_with_str(
    http: &Http,
    interaction: &ApplicationCommandInteraction,
    content: &str,
) {
    let _ = interaction
        .create_interaction_response(http, |response| {
            response
                .kind(InteractionResponseType::ChannelMessageWithSource)
                .interaction_response_data(|message| {
                    message
                        .content(content)
                        .flags(InteractionApplicationCommandCallbackDataFlags::EPHEMERAL)
                })
        })
        .await
        .map_err(|err| {
            error!("failed to reply to interaction {:?} {}", interaction, err);
            err
        });
}

pub async fn edit_interaction_response(
    http: &Http,
    interaction: &ApplicationCommandInteraction,
    content: &str,
) {
    let _ = interaction
        .edit_original_interaction_response(http, |response| response.content(content))
        .await
        .map_err(|err| {
            error!("failed to edit interaction reply {:?} {}", interaction, err);
            err
        });
}

pub async fn unauthorized_reply(
    http: &Http,
    interaction: &ApplicationCommandInteraction,
    required_permission: Permissions,
) {
    invisible_failure_reply(
        http,
        interaction,
        &format!(
            "You don't have enough permissions to execute this command! Required permission: {}",
            required_permission
        ),
    )
    .await;
}

pub async fn invisible_success_reply(
    http: &Http,
    interaction: &ApplicationCommandInteraction,
    failure_message: &str,
) {
    invisible_reply_with_str(http, interaction, failure_message).await;
}

pub async fn invisible_failure_reply(
    http: &Http,
    interaction: &ApplicationCommandInteraction,
    failure_message: &str,
) {
    invisible_reply_with_str(http, interaction, failure_message).await;
}

pub enum CleanMessagesFailure {
    Unauthorized,
    Other,
}

pub async fn clean_messages(
    http: &Http,
    channel: ChannelId,
    messages: Vec<MessageId>,
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

    let result = if new_messages.len() >= 2 {
        channel.delete_messages(http, new_messages).await
    } else if new_messages.len() == 1 {
        channel.delete_message(http, new_messages[0]).await
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
        if let Err(err) = channel.delete_message(http, message_id).await {
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
    fn discord_error_code(&self) -> Option<isize>;
}

impl SerenityErrorExt for serenity::Error {
    fn discord_error_code(&self) -> Option<isize> {
        match self {
            Error::Http(http_err) => match &**http_err {
                HttpError::UnsuccessfulRequest(response) => Some(response.error.code),
                _ => None,
            },
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
    services: &TypeMap,
    channel_id: Option<ChannelId>,
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
            guild_id = guild_id.0,
            "invalid state for mod action duration type!"
        );
    }
}
