update bans
set user_id           = $2,
    moderator_user_id = $3,
    guild_id          = $4,
    ban_time          = $5,
    expire_time       = $6,
    reason            = $7,
    expires           = $8,
    unbanned          = $9
where id = $1;