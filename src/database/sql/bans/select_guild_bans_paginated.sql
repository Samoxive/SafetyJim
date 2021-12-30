select *
from bans
where guild_id = $1
order by ban_time desc
limit 10 offset $2;