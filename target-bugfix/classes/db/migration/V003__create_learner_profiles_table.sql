create table learner_profiles (
    id uuid primary key,
    user_id uuid not null unique references users (id) on delete restrict,
    current_level varchar(2),
    target_certificate_type varchar(20),
    current_score numeric(5, 1),
    target_score numeric(5, 1),
    target_date date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_learner_profiles_set_updated_at
before update on learner_profiles
for each row execute function set_updated_at();
