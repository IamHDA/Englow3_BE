create table quiz_attempts (
    id uuid primary key,
    quiz_id uuid not null references quizzes (id) on delete restrict,
    user_id uuid not null references users (id) on delete cascade,
    status varchar(20) not null,
    started_at timestamptz not null,
    -- The deadline the server issued. A quiz with no time limit still gets one,
    -- so there is always exactly one rule about when a submission is too late.
    expires_at timestamptz not null,
    submitted_at timestamptz,
    score numeric(8, 2),
    max_score numeric(8, 2) not null,
    score_percentage numeric(5, 2),
    correct_answer_count integer,
    question_count integer not null,
    passed boolean,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_quiz_attempts_user_id_quiz_id on quiz_attempts (user_id, quiz_id);

-- One live attempt per learner per quiz, enforced here rather than only in the
-- service: two concurrent requests would otherwise both pass the check and both
-- insert.
create unique index uq_quiz_attempts_one_active
    on quiz_attempts (user_id, quiz_id)
    where status = 'IN_PROGRESS';

create trigger trg_quiz_attempts_set_updated_at
before update on quiz_attempts
for each row execute function set_updated_at();
