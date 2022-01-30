use crate::server::model::channel::ChannelModel;
use crate::server::model::guild::GuildModel;
use crate::server::model::role::RoleModel;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SettingModel {
    pub guild: GuildModel,
    pub channels: Vec<ChannelModel>,
    pub roles: Vec<RoleModel>,
    pub mod_log: bool,
    pub mod_log_channel: Option<ChannelModel>,
    pub holding_room: bool,
    pub holding_room_role: Option<RoleModel>,
    pub holding_room_minutes: i32,
    pub invite_link_remover: bool,
    pub welcome_message: bool,
    pub message: String,
    pub welcome_message_channel: Option<ChannelModel>,
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
    pub spam_filter: bool,
}
