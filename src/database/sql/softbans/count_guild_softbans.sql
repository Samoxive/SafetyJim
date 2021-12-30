select count(*)
from softbans
where guild_id = $1;