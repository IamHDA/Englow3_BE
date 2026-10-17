create table attempt_answers (
    id uuid primary key,
    exam_attempt_id uuid not null references exam_attempts (id) on delete restrict,
    question_id uuid not null references questions (id) on delete restrict,
    text_answer text,
    audio_object_key varchar(500),
    is_correct boolean,
    awarded_raw_score numeric(8, 2),
    grading_status varchar(20) not null,
    feedback jsonb,
    answered_at timestamptz not null,
    graded_at timestamptz,
    unique (exam_attempt_id, question_id)
);

create index idx_attempt_answers_question_id on attempt_answers (question_id);
create index idx_attempt_answers_grading_status on attempt_answers (grading_status);
