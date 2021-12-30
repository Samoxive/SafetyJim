select *
from iam_roles
where guild_id = $1
  and role_id = $2;