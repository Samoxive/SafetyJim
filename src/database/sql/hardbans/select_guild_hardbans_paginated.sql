select *
from hardbans
where guild_id = $1
order by hardban_time desc
limit 10 offset $2;