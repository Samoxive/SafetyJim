select count(*)
from kicks
where guild_id = $1
  and user_id = $2
  and pardoned = false;