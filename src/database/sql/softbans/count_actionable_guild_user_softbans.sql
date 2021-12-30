select count(*)
from softbans
where guild_id = $1
  and user_id = $2
  and pardoned = false;