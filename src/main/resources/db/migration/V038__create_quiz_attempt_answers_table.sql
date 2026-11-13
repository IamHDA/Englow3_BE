-- What the learner answered, kept as the text the grader compared rather than a
-- shape per question type. The five types disagree on what an answer even is -
-- an option id, a typed word, an ordered list - and nothing queries inside it:
-- it is written once at submission and read back whole for the review screen.
create table quiz_attempt_answers (
    id uuid primary key,
    quiz_attempt_id uuid not null references quiz_attempts (id) on delete cascade,
    quiz_question_id uuid not null references quiz_questions (id) on delete restrict,
    response text not null default '',
    correct boolean not null,
    awarded_points numeric(6, 2) not null,
    created_at timestamptz not null default now(),

    unique (quiz_attempt_id, quiz_question_id)
);

create index idx_quiz_attempt_answers_attempt_id on quiz_attempt_answers (quiz_attempt_id);
