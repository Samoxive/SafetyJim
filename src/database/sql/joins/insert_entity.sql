insert into joins (user_id,
                   guild_id,
                   join_time,
                   allow_time,
                   allowed)
values ($1, $2, $3, $4, $5)
returning *;