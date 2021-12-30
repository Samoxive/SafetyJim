update mutes
set unmuted = true
where guild_id = $1
  and user_id = $2;