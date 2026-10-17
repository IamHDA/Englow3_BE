-- One row per learner per card: where SM-2 has got to for that pairing. The
-- unique constraint is what makes rating a card idempotent at the storage level
-- rather than only in the service.
create table flashcard_reviews (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    flashcard_id uuid not null references flashcards (id) on delete cascade,

    status varchar(20) not null,
    repetitions smallint not null default 0,
    -- SM-2 ease factor. Starts at 2.50 and never drops below 1.30, or a card the
    -- learner keeps failing would come back every few seconds forever.
    ease_factor numeric(4, 2) not null default 2.50,
    interval_days integer not null default 0,
    due_at timestamptz not null,
    -- How many times the card was answered "again" after having been learned.
    lapse_count integer not null default 0,
    last_rating varchar(10),
    last_reviewed_at timestamptz,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    unique (user_id, flashcard_id)
);

-- The due query is the hot path: "what does this learner owe today".
create index idx_flashcard_reviews_user_id_due_at on flashcard_reviews (user_id, due_at);

create trigger trg_flashcard_reviews_set_updated_at
before update on flashcard_reviews
for each row execute function set_updated_at();
