insert into settings (guild_id,
                      mod_log,
                      mod_log_channel_id,
                      holding_room,
                      holding_room_role_id,
                      holding_room_minutes,
                      invite_link_remover,
                      welcome_message,
                      message,
                      welcome_message_channel_id,
                      prefix,
                      silent_commands,
                      no_space_prefix,
                      "statistics",
                      join_captcha,
                      silent_commands_level,
                      mod_action_confirmation_message,
                      word_filter,
                      word_filter_blocklist,
                      word_filter_level,
                      word_filter_action,
                      word_filter_action_duration,
                      word_filter_action_duration_type,
                      invite_link_remover_action,
                      invite_link_remover_action_duration,
                      invite_link_remover_action_duration_type,
                      privacy_settings,
                      privacy_mod_log,
                      softban_threshold,
                      softban_action,
                      softban_action_duration,
                      softban_action_duration_type,
                      kick_threshold,
                      kick_action,
                      kick_action_duration,
                      kick_action_duration_type,
                      mute_threshold,
                      mute_action,
                      mute_action_duration,
                      mute_action_duration_type,
                      warn_threshold,
                      warn_action,
                      warn_action_duration,
                      warn_action_duration_type,
                      mods_can_edit_tags)
values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, $16, $17, $18, $19, $20, $21, $22, $23, $24,
        $25, $26, $27, $28, $29, $30, $31, $32, $33, $34, $35, $36, $37, $38, $39, $40, $41, $42, $43, $44, $45)
returning *;