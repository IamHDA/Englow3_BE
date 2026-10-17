-- `text` is the answer. It is never sent to a learner who has not yet typed
-- theirs: the practice payload carries the audio and the hints, and the
-- transcript arrives only in the response to a submission.
create table dictation_sentences (
    id uuid primary key,
    dictation_lesson_id uuid not null references dictation_lessons (id) on delete cascade,
    order_no integer not null,

    text text not null,
    translation_vi text,
    audio_object_key varchar(500) not null,
    audio_duration_seconds integer not null,

    -- Hints, in the order the interface reveals them. word_count is derived
    -- from the text and stored anyway: it is shown before the answer exists on
    -- the client, so it cannot be counted there.
    hint_word_count integer not null,
    hint_first_letters varchar(200),
    hint_reveal_word varchar(100),
    hint_partial_transcript text,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    unique (dictation_lesson_id, order_no)
);

create index idx_dictation_sentences_lesson_id on dictation_sentences (dictation_lesson_id);

create trigger trg_dictation_sentences_set_updated_at
before update on dictation_sentences
for each row execute function set_updated_at();
