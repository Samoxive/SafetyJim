use std::sync::Arc;

use serenity::model::id::GuildId;
use sqlx::{Error, PgPool};
use std::time::Duration;
use tracing::warn;

const DEFAULT_WELCOME_MESSAGE: &str = "Welcome to $guild $user!";

pub const SILENT_COMMANDS_MOD_ONLY: i32 = 0;
pub const SILENT_COMMANDS_ALL: i32 = 1;
pub const WORD_FILTER_LEVEL_LOW: i32 = 0;
pub const WORD_FILTER_LEVEL_HIGH: i32 = 1;
pub const ACTION_NOTHING: i32 = 0;
pub const ACTION_WARN: i32 = 1;
pub const ACTION_MUTE: i32 = 2;
pub const ACTION_KICK: i32 = 3;
pub const ACTION_BAN: i32 = 4;
pub const ACTION_SOFTBAN: i32 = 5;
pub const ACTION_HARDBAN: i32 = 6;
pub const DURATION_TYPE_SECONDS: i32 = 0;
pub const DURATION_TYPE_MINUTES: i32 = 1;
pub const DURATION_TYPE_HOURS: i32 = 2;
pub const DURATION_TYPE_DAYS: i32 = 3;
pub const PRIVACY_EVERYONE: i32 = 0;
pub const PRIVACY_STAFF_ONLY: i32 = 1;
pub const PRIVACY_ADMIN_ONLY: i32 = 2;

pub fn get_action_duration_for_auto_mod_action(
    kind: i32,
    duration_type: i32,
    duration: i32,
) -> Option<Duration> {
    if kind == ACTION_BAN || kind == ACTION_MUTE {
        if duration == 0 {
            None
        } else {
            Some(Duration::from_secs(if duration == DURATION_TYPE_SECONDS {
                duration
            } else if duration == DURATION_TYPE_MINUTES {
                duration * 60
            } else if duration == DURATION_TYPE_HOURS {
                duration * 60 * 60
            } else if duration == DURATION_TYPE_DAYS {
                duration * 60 * 60 * 24
            } else {
                warn!(
                    kind = kind,
                    duration_type = duration_type,
                    "invalid state for mod action duration type!"
                );
                return None;
            } as u64))
        }
    } else {
        None
    }
}

#[derive(sqlx::FromRow)]
pub struct Setting {
    pub guild_id: i64,
    pub mod_log: bool,
    pub mod_log_channel_id: i64,
    pub holding_room: bool,
    pub holding_room_role_id: Option<i64>,
    pub holding_room_minutes: i32,
    pub invite_link_remover: bool,
    pub welcome_message: bool,
    pub message: String,
    pub welcome_message_channel_id: i64,
    pub prefix: String,        // deprecated
    pub silent_commands: bool, // deprecated
    pub no_space_prefix: bool, // deprecated
    pub statistics: bool,      // deprecated
    pub join_captcha: bool,
    pub silent_commands_level: i32,            // deprecated
    pub mod_action_confirmation_message: bool, // deprecated
    pub word_filter: bool,
    pub word_filter_blocklist: Option<String>,
    pub word_filter_level: i32,
    pub word_filter_action: i32,
    pub word_filter_action_duration: i32,
    pub word_filter_action_duration_type: i32,
    pub invite_link_remover_action: i32,
    pub invite_link_remover_action_duration: i32,
    pub invite_link_remover_action_duration_type: i32,
    pub privacy_settings: i32,
    pub privacy_mod_log: i32,
    pub softban_threshold: i32,
    pub softban_action: i32,
    pub softban_action_duration: i32,
    pub softban_action_duration_type: i32,
    pub kick_threshold: i32,
    pub kick_action: i32,
    pub kick_action_duration: i32,
    pub kick_action_duration_type: i32,
    pub mute_threshold: i32,
    pub mute_action: i32,
    pub mute_action_duration: i32,
    pub mute_action_duration_type: i32,
    pub warn_threshold: i32,
    pub warn_action: i32,
    pub warn_action_duration: i32,
    pub warn_action_duration_type: i32,
    pub mods_can_edit_tags: bool,
}

impl Setting {
    pub fn default(guild_id: GuildId) -> Self {
        Setting {
            guild_id: guild_id.0 as i64,
            mod_log: false,
            mod_log_channel_id: 0,
            holding_room: false,
            holding_room_role_id: Option::None,
            holding_room_minutes: 3,
            invite_link_remover: false,
            welcome_message: false,
            message: DEFAULT_WELCOME_MESSAGE.into(),
            welcome_message_channel_id: 0,
            prefix: String::new(),
            silent_commands: false,
            no_space_prefix: false,
            statistics: false,
            join_captcha: false,
            silent_commands_level: SILENT_COMMANDS_MOD_ONLY,
            mod_action_confirmation_message: true,
            word_filter: false,
            word_filter_blocklist: Option::None,
            word_filter_level: WORD_FILTER_LEVEL_LOW,
            word_filter_action: ACTION_WARN,
            word_filter_action_duration: 0,
            word_filter_action_duration_type: DURATION_TYPE_MINUTES,
            invite_link_remover_action: ACTION_WARN,
            invite_link_remover_action_duration: 0,
            invite_link_remover_action_duration_type: DURATION_TYPE_MINUTES,
            privacy_settings: PRIVACY_EVERYONE,
            privacy_mod_log: PRIVACY_EVERYONE,
            softban_threshold: 0,
            softban_action: ACTION_NOTHING,
            softban_action_duration: 0,
            softban_action_duration_type: DURATION_TYPE_MINUTES,
            kick_threshold: 0,
            kick_action: ACTION_NOTHING,
            kick_action_duration: 0,
            kick_action_duration_type: DURATION_TYPE_MINUTES,
            mute_threshold: 0,
            mute_action: ACTION_NOTHING,
            mute_action_duration: 0,
            mute_action_duration_type: DURATION_TYPE_MINUTES,
            warn_threshold: 0,
            warn_action: ACTION_NOTHING,
            warn_action_duration: 0,
            warn_action_duration_type: DURATION_TYPE_MINUTES,
            mods_can_edit_tags: false,
        }
    }
}

pub struct SettingsRepository(pub Arc<PgPool>);

impl SettingsRepository {
    pub async fn initialize(&self) -> Result<(), Error> {
        sqlx::query(include_str!("sql/settings/create_table.sql"))
            .execute(&*self.0)
            .await?;
        Ok(())
    }

    pub async fn fetch_setting(&self, guild_id: i64) -> Result<Option<Setting>, Error> {
        Ok(
            sqlx::query_as::<_, Setting>(include_str!("sql/settings/select_guild_setting.sql"))
                .bind(guild_id)
                .fetch_optional(&*self.0)
                .await?,
        )
    }

    pub async fn insert_setting(&self, setting: Setting) -> Result<Setting, Error> {
        Ok(
            sqlx::query_as::<_, Setting>(include_str!("sql/settings/insert_entity.sql"))
                .bind(setting.guild_id)
                .bind(setting.mod_log)
                .bind(setting.mod_log_channel_id)
                .bind(setting.holding_room)
                .bind(setting.holding_room_role_id)
                .bind(setting.holding_room_minutes)
                .bind(setting.invite_link_remover)
                .bind(setting.welcome_message)
                .bind(setting.message)
                .bind(setting.welcome_message_channel_id)
                .bind(setting.prefix)
                .bind(setting.silent_commands)
                .bind(setting.no_space_prefix)
                .bind(setting.statistics)
                .bind(setting.join_captcha)
                .bind(setting.silent_commands_level)
                .bind(setting.mod_action_confirmation_message)
                .bind(setting.word_filter)
                .bind(setting.word_filter_blocklist)
                .bind(setting.word_filter_level)
                .bind(setting.word_filter_action)
                .bind(setting.word_filter_action_duration)
                .bind(setting.word_filter_action_duration_type)
                .bind(setting.invite_link_remover_action)
                .bind(setting.invite_link_remover_action_duration)
                .bind(setting.invite_link_remover_action_duration_type)
                .bind(setting.privacy_settings)
                .bind(setting.privacy_mod_log)
                .bind(setting.softban_threshold)
                .bind(setting.softban_action)
                .bind(setting.softban_action_duration)
                .bind(setting.softban_action_duration_type)
                .bind(setting.kick_threshold)
                .bind(setting.kick_action)
                .bind(setting.kick_action_duration)
                .bind(setting.kick_action_duration_type)
                .bind(setting.mute_threshold)
                .bind(setting.mute_action)
                .bind(setting.mute_action_duration)
                .bind(setting.mute_action_duration_type)
                .bind(setting.warn_threshold)
                .bind(setting.warn_action)
                .bind(setting.warn_action_duration)
                .bind(setting.warn_action_duration_type)
                .bind(setting.mods_can_edit_tags)
                .fetch_one(&*self.0)
                .await?,
        )
    }

    pub async fn update_setting(&self, setting: Setting) -> Result<(), Error> {
        sqlx::query(include_str!("sql/settings/update_entity.sql"))
            .bind(setting.guild_id)
            .bind(setting.mod_log)
            .bind(setting.mod_log_channel_id)
            .bind(setting.holding_room)
            .bind(setting.holding_room_role_id)
            .bind(setting.holding_room_minutes)
            .bind(setting.invite_link_remover)
            .bind(setting.welcome_message)
            .bind(setting.message)
            .bind(setting.welcome_message_channel_id)
            .bind(setting.prefix)
            .bind(setting.silent_commands)
            .bind(setting.no_space_prefix)
            .bind(setting.statistics)
            .bind(setting.join_captcha)
            .bind(setting.silent_commands_level)
            .bind(setting.mod_action_confirmation_message)
            .bind(setting.word_filter)
            .bind(setting.word_filter_blocklist)
            .bind(setting.word_filter_level)
            .bind(setting.word_filter_action)
            .bind(setting.word_filter_action_duration)
            .bind(setting.word_filter_action_duration_type)
            .bind(setting.invite_link_remover_action)
            .bind(setting.invite_link_remover_action_duration)
            .bind(setting.invite_link_remover_action_duration_type)
            .bind(setting.privacy_settings)
            .bind(setting.privacy_mod_log)
            .bind(setting.softban_threshold)
            .bind(setting.softban_action)
            .bind(setting.softban_action_duration)
            .bind(setting.softban_action_duration_type)
            .bind(setting.kick_threshold)
            .bind(setting.kick_action)
            .bind(setting.kick_action_duration)
            .bind(setting.kick_action_duration_type)
            .bind(setting.mute_threshold)
            .bind(setting.mute_action)
            .bind(setting.mute_action_duration)
            .bind(setting.mute_action_duration_type)
            .bind(setting.warn_threshold)
            .bind(setting.warn_action)
            .bind(setting.warn_action_duration)
            .bind(setting.warn_action_duration_type)
            .bind(setting.mods_can_edit_tags)
            .execute(&*self.0)
            .await?;

        Ok(())
    }

    pub async fn delete_setting(&self, guild_id: i64) -> Result<(), Error> {
        sqlx::query(include_str!("sql/settings/delete_setting.sql"))
            .bind(guild_id)
            .execute(&*self.0)
            .await?;

        Ok(())
    }
}
