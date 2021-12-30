select count(*)
from bans
where guild_id = $1;