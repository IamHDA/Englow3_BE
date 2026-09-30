create table user_tour_completions (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    role varchar(20) not null check (role in ('LEARNER', 'STAFF', 'ADMIN')),
    version integer not null check (version > 0),
    completed_at timestamptz not null default now(),
    constraint uq_user_tour_completions_user_role_version unique (user_id, role, version)
);
