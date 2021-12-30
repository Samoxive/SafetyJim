create table if not exists warns
(
    id                serial  not null primary key,
    user_id           bigint  not null,
    moderator_user_id bigint  not null,
    guild_id          bigint  not null,
    warn_time         bigint  not null,
    reason            text    not null,
    pardoned          boolean not null
);