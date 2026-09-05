alter table learning_path_items drop constraint learning_path_items_status_check;
alter table learning_path_items
    add constraint learning_path_items_status_check
        check (status in ('PENDING', 'POSTPONED', 'COMPLETED', 'SKIPPED')),
    add column content_type varchar(30),
    add column content_id text,
    add column content_difficulty numeric(4, 3) check (content_difficulty is null or content_difficulty between 0 and 1),
    add column postponed_until timestamptz,
    add column skip_reason varchar(500),
    add constraint chk_learning_path_content_reference
        check ((content_type is null) = (content_id is null)
               and (content_type is null) = (content_difficulty is null)),
    add constraint chk_learning_path_postponed
        check (status = 'POSTPONED' or postponed_until is null);

alter table learning_events
    add column idempotency_key varchar(200),
    add column duration_seconds integer check (duration_seconds is null or duration_seconds >= 0),
    add column difficulty numeric(4, 3) check (difficulty is null or difficulty between 0 and 1),
    add column metadata jsonb not null default '{}'::jsonb check (jsonb_typeof(metadata) = 'object');

create unique index uq_learning_events_user_idempotency
    on learning_events (user_id, idempotency_key) where idempotency_key is not null;

create table learner_mastery_events (
    event_id uuid primary key references learning_events (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    concept_id text not null references concepts (concept_id) on delete restrict,
    successful boolean not null,
    prior_probability numeric(8, 7) not null check (prior_probability between 0 and 1),
    observed_probability numeric(8, 7) not null check (observed_probability between 0 and 1),
    posterior_probability numeric(8, 7) not null check (posterior_probability between 0 and 1),
    p_learn numeric(8, 7) not null,
    p_slip numeric(8, 7) not null,
    p_guess numeric(8, 7) not null,
    algorithm_version integer not null,
    created_at timestamptz not null default now()
);

create table learning_path_item_replacements (
    id uuid primary key,
    learning_path_item_id uuid not null references learning_path_items (id) on delete cascade,
    old_content_type varchar(30) not null,
    old_content_id text not null,
    new_content_type varchar(30) not null,
    new_content_id text not null,
    reason varchar(500) not null,
    created_at timestamptz not null default now(),
    check (old_content_type <> new_content_type or old_content_id <> new_content_id)
);
