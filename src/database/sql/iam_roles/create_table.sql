create table if not exists iam_roles
(
    id       serial not null primary key,
    guild_id bigint not null,
    role_id  bigint not null
);