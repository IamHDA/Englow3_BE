create table exams (
    id uuid primary key,
    title varchar(100) not null,
    description text not null,
    exam_type varchar(20) not null,
    certificate_type varchar(20),
    certificate_variant varchar(30),
    target_level varchar(2),
    duration_seconds integer not null,
    max_raw_score numeric(8, 2) not null,
    pass_score numeric(5, 1),
    status varchar(20) not null,
    version_number integer not null,
    created_by_user_id uuid not null references users (id) on delete restrict,
    published_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_exams_created_by_user_id on exams (created_by_user_id);

create trigger trg_exams_set_updated_at
before update on exams
for each row execute function set_updated_at();
