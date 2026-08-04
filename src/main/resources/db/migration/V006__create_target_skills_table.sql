create table target_skills (
    id integer generated always as identity primary key,
    skill_code varchar(20) not null,
    display_name varchar(100) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_target_skills_set_updated_at
before update on target_skills
for each row execute function set_updated_at();
