-- The ordered word lists three of the five question types need, in one table
-- keyed by what the list is for:
--
--   ACCEPTED_ANSWER  FILL_BLANK - any of these counts as right
--   WORD_BANK        REWRITE    - the words offered to build an answer from
--   CORRECT_WORD     REWRITE    - the answer, in order
--   SCRAMBLED        REORDER    - the words as shown, shuffled
--   CORRECT_ORDER    REORDER    - the answer, in order
--
-- One table rather than five: every one of them is "an ordered list of strings
-- belonging to a question", and five tables with identical columns would be
-- five repositories and five joins for no distinction that matters.
create table quiz_question_tokens (
    id uuid primary key,
    quiz_question_id uuid not null references quiz_questions (id) on delete cascade,
    role varchar(20) not null,
    order_no integer not null,
    value text not null,

    unique (quiz_question_id, role, order_no)
);

create index idx_quiz_question_tokens_question_id_role
    on quiz_question_tokens (quiz_question_id, role);
