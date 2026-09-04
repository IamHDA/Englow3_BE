create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key,
    email varchar(320) not null,
    raw_user_meta_data jsonb not null default '{}'::jsonb
);
