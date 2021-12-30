update kicks
set user_id           = $2,
    moderator_user_id = $3,
    guild_id          = $4,
    kick_time         = $5,
    reason            = $6,
    pardoned          = $7
where id = $1;