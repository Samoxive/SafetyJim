create table if not exists user_secrets
(
    user_id      bigint not null primary key,
    access_token text   not null,
    update_time  bigint not null
);