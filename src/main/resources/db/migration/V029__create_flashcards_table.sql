-- Column names follow data_pipeline/schemas/flashcard.py so the 3,000 generated
-- cards load without a translation step. Audio is an object key rather than the
-- pipeline's audio_url: the same rule the exam module follows, so the URL is
-- signed at the boundary and expires on its own.
create table flashcards (
    id uuid primary key,
    flashcard_set_id uuid not null references flashcard_sets (id) on delete cascade,
    order_no integer not null,

    lemma varchar(120) not null,
    part_of_speech varchar(20) not null,
    -- Which sense of the word this card teaches; "bank (river)" is not "bank (money)".
    sense_label varchar(200) not null,

    -- US is required because the catalogue is TOEIC, which is American English.
    ipa_us varchar(120) not null,
    ipa_uk varchar(120),
    audio_us_object_key varchar(500),
    audio_uk_object_key varchar(500),

    definition_en text not null,
    definition_vi text not null,
    example_sentence text not null,
    example_translation_vi text,
    mnemonic_tip_vi text,

    cefr_level varchar(2),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    unique (flashcard_set_id, order_no)
);

create index idx_flashcards_flashcard_set_id on flashcards (flashcard_set_id);

create trigger trg_flashcards_set_updated_at
before update on flashcards
for each row execute function set_updated_at();
