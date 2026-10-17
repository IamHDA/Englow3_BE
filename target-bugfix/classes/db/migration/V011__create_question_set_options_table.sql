create table question_set_options (
    id uuid primary key,
    question_set_id uuid not null references question_sets (id) on delete restrict,
    option_label varchar(10) not null,
    content text,
    order_no integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_question_set_options_question_set_id on question_set_options (question_set_id);

create trigger trg_question_set_options_set_updated_at
before update on question_set_options
for each row execute function set_updated_at();
