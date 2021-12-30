update hardbans
set user_id           = $2,
    moderator_user_id = $3,
    guild_id          = $4,
    hardban_time      = $5,
    reason            = $6
where id = $1;