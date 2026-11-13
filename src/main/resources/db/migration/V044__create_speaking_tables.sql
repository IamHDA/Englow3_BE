-- Speaking practice: a sentence to say, a recording of someone saying it, and
-- what the assessment made of it.

create table speaking_prompts (
    id uuid primary key,
    slug varchar(120) not null unique,
    title varchar(200) not null,
    category varchar(60) not null,
    -- CEFR band the prompt is pitched at. Null on one that deliberately mixes levels.
    target_level varchar(2),

    -- What the learner is asked to say. Not optional: an accuracy score is
    -- accuracy against something, and without this the provider is doing plain
    -- transcription and the number would mean nothing.
    reference_text text not null,
    ipa_transcript text,
    translation_vi text,
    -- The sound being drilled, e.g. "/iː/ vs /ɪ/". Free text - it is shown, not matched on.
    phoneme_target varchar(100),
    -- Coaching notes, in the order they are shown. jsonb rather than a child
    -- table because they are read as a unit with the prompt and never queried
    -- across; a table would buy a join and nothing else.
    tips jsonb not null default '[]',

    status varchar(20) not null,
    created_by_user_id uuid not null references users (id) on delete restrict,
    published_at timestamptz,

    -- The same review trail V042 and V043 gave the other content types.
    submitted_for_review_at timestamptz,
    reviewed_by_user_id uuid references users (id) on delete restrict,
    reviewed_at timestamptz,
    review_note text,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_speaking_prompts_status on speaking_prompts (status);

create trigger trg_speaking_prompts_set_updated_at
before update on speaking_prompts
for each row execute function set_updated_at();

create table speaking_attempts (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    -- restrict, not cascade: a prompt is archived rather than deleted, and a
    -- recording someone made is theirs to keep looking at.
    speaking_prompt_id uuid not null references speaking_prompts (id) on delete restrict,

    -- The object key, not a URL. URLs here are signed and expire; the key is
    -- what the row means, and a fresh URL is derived from it on every read.
    audio_object_key varchar(500) not null,
    audio_content_type varchar(60) not null,

    status varchar(20) not null,

    -- Null until assessed. Five separate columns rather than one jsonb blob
    -- because these are the numbers screens sort, average and chart.
    accuracy_percent numeric(5, 2),
    fluency_percent numeric(5, 2),
    completeness_percent numeric(5, 2),
    prosody_percent numeric(5, 2),
    pronunciation_percent numeric(5, 2),
    recognized_text text,

    -- Why the assessment did not produce a score, when it did not.
    error_code varchar(100),

    created_at timestamptz not null default now(),
    assessed_at timestamptz,
    updated_at timestamptz not null default now()
);

create index idx_speaking_attempts_user_id_created_at
    on speaking_attempts (user_id, created_at desc);
create index idx_speaking_attempts_user_id_prompt_id
    on speaking_attempts (user_id, speaking_prompt_id);

create trigger trg_speaking_attempts_set_updated_at
before update on speaking_attempts
for each row execute function set_updated_at();

create table speaking_attempt_words (
    id uuid primary key,
    speaking_attempt_id uuid not null references speaking_attempts (id) on delete cascade,
    order_no integer not null,

    word varchar(100) not null,
    accuracy_percent numeric(5, 2),
    -- The provider's own label: Mispronunciation, Omission, Insertion, None.
    error_type varchar(40),
    offset_ms integer,
    duration_ms integer,

    -- Per-phoneme scores for this word. jsonb for the same reason as tips: read
    -- with the word, never queried across, and a third table to hold a handful
    -- of symbols each would triple the row count for nothing.
    phonemes jsonb not null default '[]',

    unique (speaking_attempt_id, order_no)
);

create index idx_speaking_attempt_words_attempt_id
    on speaking_attempt_words (speaking_attempt_id);
