update joins
set user_id    = $2,
    guild_id   = $3,
    join_time  = $4,
    allow_time = $5,
    allowed    = $6
where id = $1;