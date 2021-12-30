select count(*)
from warns
where guild_id = $1;