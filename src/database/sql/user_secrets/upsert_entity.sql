insert into user_secrets (user_id, access_token, update_time)
values ($1, $2, $3)
on conflict (user_id) do update
    set access_token = excluded.access_token,
        update_time  = excluded.update_time;