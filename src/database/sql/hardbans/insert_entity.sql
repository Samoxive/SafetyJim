insert into hardbans (user_id,
                      moderator_user_id,
                      guild_id,
                      hardban_time,
                      reason)
values ($1, $2, $3, $4, $5)
returning *;