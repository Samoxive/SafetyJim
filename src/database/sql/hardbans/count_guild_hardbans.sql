select count(*)
from hardbans
where guild_id = $1;