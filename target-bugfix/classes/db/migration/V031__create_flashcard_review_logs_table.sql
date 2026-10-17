-- Append-only: one row per rating the learner gives. flashcard_reviews holds
-- only where SM-2 stands now, which cannot answer "how much did I study last
-- week" or "how many days in a row" - both need the events, not the state.
--
-- flashcard_set_id is denormalised from the card on purpose. Every history and
-- statistics query groups by set, and carrying it here keeps those reads off a
-- join with a table that is much larger.
create table flashcard_review_logs (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    flashcard_id uuid not null references flashcards (id) on delete cascade,
    flashcard_set_id uuid not null references flashcard_sets (id) on delete cascade,

    rating varchar(10) not null,
    time_spent_seconds integer not null,
    reviewed_at timestamptz not null default now()
);

create index idx_flashcard_review_logs_user_id_reviewed_at
    on flashcard_review_logs (user_id, reviewed_at desc);
