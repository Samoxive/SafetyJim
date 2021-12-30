insert into iam_roles (guild_id,
                       role_id)
values ($1, $2) RETURNING *;