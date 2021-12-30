update tags
set guild_id = $2,
    "name"   = $3,
    response = $4
where id = $1;