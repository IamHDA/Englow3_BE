drop table user_target_skills;

drop table target_skills;

create table user_target_skills (
    user_id uuid not null references users (id) on delete restrict,
    skill varchar(20) not null,
    created_at timestamptz not null default now(),
    primary key (user_id, skill)
);
