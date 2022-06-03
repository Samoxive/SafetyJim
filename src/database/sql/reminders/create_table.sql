create table if not exists reminders
(
    id          serial  not null primary key,
    user_id     bigint  not null,
    channel_id  bigint  not null,
    guild_id    bigint  not null,
    create_time bigint  not null,
    remind_time bigint  not null,
    reminded    boolean not null,
    message     text    not null
);