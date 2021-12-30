create table if not exists tags
(
    id       serial not null primary key,
    guild_id bigint not null,
    name     text   not null,
    response text   not null
);