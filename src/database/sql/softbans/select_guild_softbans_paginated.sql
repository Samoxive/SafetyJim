select *
from softbans
where guild_id = $1
order by softban_time desc
limit 10 offset $2;