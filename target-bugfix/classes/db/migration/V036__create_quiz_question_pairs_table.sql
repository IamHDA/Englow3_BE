-- MATCHING only: the left half and the right half that belongs with it. The
-- interface shuffles the right column when it renders; the stored order is the
-- answer key.
create table quiz_question_pairs (
    id uuid primary key,
    quiz_question_id uuid not null references quiz_questions (id) on delete cascade,
    order_no integer not null,
    left_text text not null,
    right_text text not null,

    unique (quiz_question_id, order_no)
);

create index idx_quiz_question_pairs_question_id on quiz_question_pairs (quiz_question_id);
