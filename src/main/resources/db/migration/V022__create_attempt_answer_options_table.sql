create table attempt_answer_options (
    id uuid primary key,
    attempt_answer_id uuid not null references attempt_answers (id) on delete restrict,
    question_option_id uuid references question_options (id) on delete restrict,
    question_set_option_id uuid references question_set_options (id) on delete restrict,
    created_at timestamptz not null default now(),
    check (
        (question_option_id is not null and question_set_option_id is null)
        or (question_option_id is null and question_set_option_id is not null)
    )
);

create unique index uq_attempt_answer_options_question_option
    on attempt_answer_options (attempt_answer_id, question_option_id)
    where question_option_id is not null;

create unique index uq_attempt_answer_options_question_set_option
    on attempt_answer_options (attempt_answer_id, question_set_option_id)
    where question_set_option_id is not null;
