create table if not exists bans
(
    id                serial  not null primary key,
    user_id           bigint  not null,
    moderator_user_id bigint  not null,
    guild_id          bigint  not null,
    ban_time          bigint  not null,
    expire_time       bigint  not null,
    reason            text    not null,
    expires           boolean not null,
    unbanned          boolean not null
);
