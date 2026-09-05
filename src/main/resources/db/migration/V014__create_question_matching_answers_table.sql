create table question_matching_answers (
    question_id uuid not null references questions (id) on delete restrict,
    question_set_option_id uuid not null references question_set_options (id) on delete restrict,
    created_at timestamptz not null default now(),
    primary key (question_id, question_set_option_id)
);

create index idx_question_matching_answers_question_set_option_id on question_matching_answers (question_set_option_id);
