update reminders
set user_id      = $2,
    channel_id   = $3,
    guild_id     = $4,
    create_time = $5,
    remind_time  = $6,
    reminded     = $7,
    message      = $8
where id = $1;