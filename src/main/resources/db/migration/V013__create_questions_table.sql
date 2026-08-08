create table questions (
    id uuid primary key,
    question_set_id uuid not null references question_sets (id) on delete restrict,
    question_type varchar(20) not null,
    content text not null,
    difficulty_level varchar(20) not null,
    skill_type varchar(20) not null,
    question_category varchar(40),
    order_no integer not null,
    max_raw_score numeric(8, 2) not null,
    explanation text,
    metadata jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_questions_question_set_id_order_no on questions (question_set_id, order_no);
create index idx_questions_skill_type_question_category on questions (skill_type, question_category);

create trigger trg_questions_set_updated_at
before update on questions
for each row execute function set_updated_at();
