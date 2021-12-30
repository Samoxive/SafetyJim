select *
from mutes
where guild_id = $1
order by mute_time desc
limit 10 offset $2;