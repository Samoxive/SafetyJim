select count(*)
from mutes
where guild_id = $1;