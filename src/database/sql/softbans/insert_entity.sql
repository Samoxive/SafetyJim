insert into softbans (user_id,
                      moderator_user_id,
                      guild_id,
                      softban_time,
                      reason,
                      pardoned)
values ($1, $2, $3, $4, $5, $6)
returning *;