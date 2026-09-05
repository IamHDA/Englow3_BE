create table users (
    id uuid primary key,
    auth_provider_id uuid not null unique,
    email varchar(320) not null,
    role varchar(30) not null default 'LEARNER',
    full_name varchar(60) not null,
    display_name varchar(60) not null,
    avatar_object_key varchar(500),
    banner_object_key varchar(500),
    is_onboarding_completed boolean not null default false,
    onboarding_step varchar(30) not null default 'LEARNING_PURPOSES',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_users_set_updated_at
before update on users
for each row execute function set_updated_at();
