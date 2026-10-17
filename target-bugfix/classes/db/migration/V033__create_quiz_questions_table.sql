-- The five shapes the interface can render. Everything type-specific hangs off
-- one of the three child tables rather than living in a payload column here, so
-- a malformed question is a constraint violation rather than a runtime surprise.
create table quiz_questions (
    id uuid primary key,
    quiz_id uuid not null references quizzes (id) on delete cascade,
    order_no integer not null,
    question_type varchar(20) not null,
    title varchar(200) not null,
    prompt text not null,
    points smallint not null,
    explanation text not null default '',

    -- FILL_BLANK renders prompt as "<before> ___ <after>"; null for every other type.
    before_text text,
    after_text text,
    -- REWRITE shows the sentence to rework and the word that must appear in the answer.
    original_sentence text,
    rewrite_keyword varchar(100),

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    unique (quiz_id, order_no)
);

create index idx_quiz_questions_quiz_id on quiz_questions (quiz_id);

create trigger trg_quiz_questions_set_updated_at
before update on quiz_questions
for each row execute function set_updated_at();
