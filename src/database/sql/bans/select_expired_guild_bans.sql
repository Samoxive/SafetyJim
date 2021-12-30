select *
from bans
where unbanned = false
  and expires = true
  and expire_time < $1;