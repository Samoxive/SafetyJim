create table if not exists mutes
(
    id                serial  not null primary key,
    user_id           bigint  not null,
    moderator_user_id bigint  not null,
    guild_id          bigint  not null,
    mute_time         bigint  not null,
    expire_time       bigint  not null,
    reason            text    not null,
    expires           boolean not null,
    unmuted           boolean not null,
    pardoned          boolean not null
);