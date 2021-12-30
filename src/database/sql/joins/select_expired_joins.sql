select *
from joins
where allowed = false
  and allow_time < $1;