-- The slice of Supabase that the migrations reach for.
--
-- V024 and V026 put triggers on auth.users, which Supabase provides and a bare
-- Postgres does not. Only the columns those two triggers touch are here: the
-- point is to let the real migrations run unchanged, not to reimplement GoTrue.
-- Anything more would be a second, wrong copy of someone else's schema.
create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key default gen_random_uuid(),
    email text not null unique,
    raw_user_meta_data jsonb not null default '{}'::jsonb,
    raw_app_meta_data jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
