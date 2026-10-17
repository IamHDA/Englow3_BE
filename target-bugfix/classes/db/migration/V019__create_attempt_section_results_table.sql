create table attempt_section_results (
    id uuid primary key,
    exam_attempt_id uuid not null references exam_attempts (id) on delete restrict,
    section_type varchar(20) not null,
    raw_score numeric(8, 2),
    max_raw_score numeric(8, 2) not null,
    converted_score numeric(5, 1),
    correct_answer_count integer,
    question_count integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (exam_attempt_id, section_type)
);

create trigger trg_attempt_section_results_set_updated_at
before update on attempt_section_results
for each row execute function set_updated_at();
