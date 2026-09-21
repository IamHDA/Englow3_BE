-- One row per submission, not one per sentence: a learner who retypes a
-- sentence is practising, and keeping both rows is what lets "this one keeps
-- catching me out" be answered later.
create table dictation_attempts (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    dictation_lesson_id uuid not null references dictation_lessons (id) on delete cascade,
    dictation_sentence_id uuid not null references dictation_sentences (id) on delete cascade,

    response text not null,
    accuracy_percent numeric(5, 2) not null,
    correct_word_count integer not null,
    total_word_count integer not null,
    attempted_at timestamptz not null default now()
);

create index idx_dictation_attempts_user_id_attempted_at
    on dictation_attempts (user_id, attempted_at desc);

-- "Which sentences am I worst at" groups by sentence for one learner.
create index idx_dictation_attempts_user_id_sentence_id
    on dictation_attempts (user_id, dictation_sentence_id);
