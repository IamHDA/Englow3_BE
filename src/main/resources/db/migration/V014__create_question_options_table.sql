create table question_options (
    id uuid primary key,
    question_id uuid not null references questions (id) on delete restrict,
    content text not null,
    order_no integer not null,
    is_correct boolean not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_question_options_question_id on question_options (question_id);

create trigger trg_question_options_set_updated_at
before update on question_options
for each row execute function set_updated_at();
