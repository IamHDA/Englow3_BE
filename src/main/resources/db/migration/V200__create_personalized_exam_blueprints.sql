create table personalized_exam_blueprints (
    exam_id uuid primary key references exams (id) on delete restrict,
    user_id uuid not null references users (id) on delete cascade,
    target_level varchar(2) not null check (target_level in ('A1', 'A2', 'B1', 'B2', 'C1')),
    requested_skill varchar(20) not null check (requested_skill in ('LISTENING', 'READING', 'MIXED')),
    requested_questions integer not null check (requested_questions between 5 and 100),
    difficulty_min numeric(4, 3) not null check (difficulty_min between 0 and 1),
    difficulty_max numeric(4, 3) not null check (difficulty_max between 0 and 1),
    selection_policy_version integer not null,
    selection_seed uuid not null,
    request_hash varchar(64) not null check (request_hash ~ '^[0-9a-f]{64}$'),
    idempotency_key varchar(200) not null,
    created_at timestamptz not null default now(),
    unique (user_id, idempotency_key),
    check (difficulty_max >= difficulty_min)
);

create table personalized_exam_sources (
    exam_id uuid not null references personalized_exam_blueprints (exam_id) on delete restrict,
    position integer not null check (position > 0),
    source_item_id text not null references exam_items (item_id) on delete restrict,
    source_group_id text not null references exam_groups (group_id) on delete restrict,
    source_review_status varchar(30) not null,
    source_content_hash varchar(64) not null check (source_content_hash ~ '^[0-9a-f]{64}$'),
    primary key (exam_id, position),
    unique (exam_id, source_item_id)
);

create index idx_personalized_exam_blueprints_user_created
    on personalized_exam_blueprints (user_id, created_at desc);
create index idx_personalized_exam_sources_item on personalized_exam_sources (source_item_id);
