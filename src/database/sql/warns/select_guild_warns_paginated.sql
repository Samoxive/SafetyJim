select *
from warns
where guild_id = $1
order by warn_time desc
limit 10 offset $2;