select *
from reminders
where reminded = false
  and remind_time < $1;