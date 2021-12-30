create table if not exists hardbans
(
    id                serial not null primary key,
    user_id           bigint not null,
    moderator_user_id bigint not null,
    guild_id          bigint not null,
    hardban_time      bigint not null,
    reason            text   not null
);