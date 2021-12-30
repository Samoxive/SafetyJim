select *
from mutes
where guild_id = $1
  and user_id = $2
  and unmuted = false;