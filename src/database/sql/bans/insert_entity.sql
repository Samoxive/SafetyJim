insert into bans (user_id,
                  moderator_user_id,
                  guild_id,
                  ban_time,
                  expire_time,
                  reason,
                  expires,
                  unbanned)
values ($1, $2, $3, $4, $5, $6, $7, $8)
returning *;