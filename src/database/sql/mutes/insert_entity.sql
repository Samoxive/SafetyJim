insert into mutes (user_id,
                   moderator_user_id,
                   guild_id,
                   mute_time,
                   expire_time,
                   reason,
                   expires,
                   unmuted,
                   pardoned)
values ($1, $2, $3, $4, $5, $6, $7, $8, $9)
returning *;