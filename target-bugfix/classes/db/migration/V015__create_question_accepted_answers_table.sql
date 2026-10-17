create table question_accepted_answers (
    id uuid primary key,
    question_id uuid not null references questions (id) on delete restrict,
    answer_text text not null,
    is_case_sensitive boolean not null default false,
    order_no integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_question_accepted_answers_question_id on question_accepted_answers (question_id);

create trigger trg_question_accepted_answers_set_updated_at
before update on question_accepted_answers
for each row execute function set_updated_at();
