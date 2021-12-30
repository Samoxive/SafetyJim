select *
from mutes
where unmuted = false
  and expires = true
  and expire_time < $1;