create schema if not exists auth;

-- Only the columns the migrations' triggers touch: V024/V025 read email and
-- raw_user_meta_data when a user signs up, and V026 writes the role into
-- raw_app_meta_data whenever englow3.users.role changes.
create table if not exists auth.users (
    id uuid primary key,
    email varchar(320) not null,
    raw_user_meta_data jsonb not null default '{}'::jsonb,
    raw_app_meta_data jsonb not null default '{}'::jsonb
);

alter table auth.users add column if not exists raw_app_meta_data jsonb not null default '{}'::jsonb;
