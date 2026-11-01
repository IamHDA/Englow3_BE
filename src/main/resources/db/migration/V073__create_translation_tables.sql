-- Translation practice: Vietnamese sentences a learner puts into English, and
-- what the grader made of each attempt.

create table translation_sets (
    id uuid primary key,
    slug varchar(120) not null unique,
    title varchar(200) not null,
    topic varchar(60) not null,
    -- CEFR band the set is pitched at. Null on one that deliberately mixes levels.
    target_level varchar(2),
    description text,

    status varchar(20) not null,
    created_by_user_id uuid not null references users (id) on delete restrict,
    published_at timestamptz,

    -- The review trail every other authored content type carries.
    submitted_for_review_at timestamptz,
    reviewed_by_user_id uuid references users (id) on delete restrict,
    reviewed_at timestamptz,
    review_note text,

    version bigint not null default 0,
    authoring_updated_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_translation_sets_status on translation_sets (status);

create trigger trg_translation_sets_set_updated_at
before update on translation_sets
for each row execute function set_updated_at();

create table translation_items (
    id uuid primary key,
    translation_set_id uuid not null references translation_sets (id) on delete cascade,
    order_no integer not null check (order_no > 0),
    -- The Vietnamese the learner translates.
    source_vi text not null,
    -- One good English rendering. The grader judges meaning against the source,
    -- so this guides it rather than being the only accepted answer.
    reference_en text not null,
    -- Shown after an attempt: an idiom, a register choice, a common trap.
    notes text,
    created_at timestamptz not null default now(),
    unique (translation_set_id, order_no)
);

create table translation_attempts (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    -- restrict, not cascade: a set is archived rather than deleted, and the
    -- learner's graded work stays readable.
    translation_set_id uuid not null references translation_sets (id) on delete restrict,
    translation_item_id uuid not null references translation_items (id) on delete restrict,
    response text not null,

    status varchar(20) not null,
    -- Null until graded.
    score integer check (score between 0 and 100),
    -- The grader's report as returned and validated: verdict, errors, a better
    -- version. Read as a unit with the attempt, never queried inside.
    feedback jsonb,
    -- Why grading produced nothing, when it did not.
    error_code varchar(100),

    created_at timestamptz not null default now(),
    graded_at timestamptz,
    updated_at timestamptz not null default now()
);

create index idx_translation_attempts_user_id_created_at
    on translation_attempts (user_id, created_at desc);
create index idx_translation_attempts_user_id_item_id
    on translation_attempts (user_id, translation_item_id);

create trigger trg_translation_attempts_set_updated_at
before update on translation_attempts
for each row execute function set_updated_at();
