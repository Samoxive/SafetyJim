update mutes
set user_id           = $2,
    moderator_user_id = $3,
    guild_id          = $4,
    mute_time         = $5,
    expire_time       = $6,
    reason            = $7,
    expires           = $8,
    unmuted           = $9,
    pardoned          = $10
where id = $1;