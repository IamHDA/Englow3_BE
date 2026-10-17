create table dictation_lessons (
    id uuid primary key,
    slug varchar(120) not null unique,
    title varchar(200) not null,
    topic varchar(60) not null,
    -- CEFR band the clip is pitched at. Null on a mixed lesson.
    target_level varchar(2),
    status varchar(20) not null,
    created_by_user_id uuid not null references users (id) on delete restrict,
    published_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_dictation_lessons_status on dictation_lessons (status);

create trigger trg_dictation_lessons_set_updated_at
before update on dictation_lessons
for each row execute function set_updated_at();
