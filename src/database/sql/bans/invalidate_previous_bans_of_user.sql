update bans
set unbanned = true
where guild_id = $1
  and user_id = $2;