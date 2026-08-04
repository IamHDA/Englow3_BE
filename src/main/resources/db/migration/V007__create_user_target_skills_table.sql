create table user_target_skills (
    user_id uuid not null references users (id) on delete restrict,
    target_skill_id integer not null references target_skills (id) on delete restrict,
    created_at timestamptz not null default now(),
    primary key (user_id, target_skill_id)
);

create index idx_user_target_skills_target_skill_id on user_target_skills (target_skill_id);
