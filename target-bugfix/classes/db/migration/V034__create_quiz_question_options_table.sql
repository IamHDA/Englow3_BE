-- Multiple choice only. Separate from the exam module's question_options: that
-- table belongs to the exam bounded context, and one shared table would tie a
-- change in either to the other.
create table quiz_question_options (
    id uuid primary key,
    quiz_question_id uuid not null references quiz_questions (id) on delete cascade,
    order_no integer not null,
    -- The letter the interface shows: A, B, C, D.
    label varchar(4) not null,
    content text not null,
    correct boolean not null default false,

    unique (quiz_question_id, order_no)
);

create index idx_quiz_question_options_question_id on quiz_question_options (quiz_question_id);
