create table exam_attempts (
    id uuid primary key,
    exam_id uuid not null references exams (id) on delete restrict,
    exam_version_number integer not null,
    user_id uuid not null references users (id) on delete restrict,
    status varchar(20) not null,
    started_at timestamptz not null,
    expires_at timestamptz not null,
    submitted_at timestamptz,
    scored_at timestamptz,
    raw_score numeric(8, 2),
    max_raw_score numeric(8, 2) not null,
    converted_score numeric(5, 1),
    score_percentage numeric(5, 2),
    correct_answer_count integer,
    question_count integer not null,
    assessed_level varchar(2),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_exam_attempts_user_id_status on exam_attempts (user_id, status);
create index idx_exam_attempts_exam_id on exam_attempts (exam_id);

create trigger trg_exam_attempts_set_updated_at
before update on exam_attempts
for each row execute function set_updated_at();
