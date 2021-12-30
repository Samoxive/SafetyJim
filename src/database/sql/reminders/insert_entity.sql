insert into reminders (user_id,
                       channel_id,
                       guild_id,
                       create_time,
                       remind_time,
                       reminded,
                       message)
values ($1, $2, $3, $4, $5, $6, $7)
returning *;