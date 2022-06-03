create table if not exists joins
(
    id         serial  not null primary key,
    user_id    bigint  not null,
    guild_id   bigint  not null,
    join_time  bigint  not null,
    allow_time bigint  not null,
    allowed    boolean not null
);