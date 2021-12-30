select *
from tags
where guild_id = $1
  and name = $2;