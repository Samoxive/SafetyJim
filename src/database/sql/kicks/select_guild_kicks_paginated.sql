select *
from kicks
where guild_id = $1
order by kick_time desc
limit 10 offset $2;