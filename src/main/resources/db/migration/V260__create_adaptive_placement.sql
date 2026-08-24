create table irt_calibration_versions (
    version integer primary key check (version > 0),
    source_hash varchar(64) not null unique check (source_hash ~ '^[0-9a-f]{64}$'),
    minimum_responses integer not null check (minimum_responses >= 30),
    status varchar(20) not null check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    created_by uuid references users (id) on delete set null,
    activated_by uuid references users (id) on delete set null,
    created_at timestamptz not null default now(),
    activated_at timestamptz
);

create unique index uq_irt_calibration_active
    on irt_calibration_versions ((status)) where status = 'ACTIVE';

create table irt_item_parameters (
    calibration_version integer not null references irt_calibration_versions (version) on delete restrict,
    item_id text not null references exam_items (item_id) on delete restrict,
    discrimination numeric(8, 5) not null check (discrimination > 0 and discrimination <= 5),
    difficulty numeric(8, 5) not null check (difficulty between -6 and 6),
    guessing numeric(8, 5) not null check (guessing >= 0 and guessing < 0.5),
    response_count integer not null check (response_count >= 0),
    standard_error numeric(8, 5) check (standard_error is null or standard_error > 0),
    primary key (calibration_version, item_id)
);

create table adaptive_placement_attempts (
    id uuid primary key,
    user_id uuid not null references users (id) on delete restrict,
    status varchar(20) not null check (status in ('IN_PROGRESS', 'COMPLETED', 'FALLBACK')),
    calibration_version integer references irt_calibration_versions (version) on delete restrict,
    fallback_exam_attempt_id uuid references exam_attempts (id) on delete restrict,
    current_theta numeric(8, 5) not null default 0,
    standard_error numeric(8, 5),
    selected_item_id text references exam_items (item_id) on delete restrict,
    response_count integer not null default 0 check (response_count >= 0),
    min_items integer not null check (min_items > 0),
    max_items integer not null check (max_items >= min_items),
    assessed_level varchar(2) check (assessed_level in ('A1', 'A2', 'B1', 'B2', 'C1')),
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    check ((status = 'FALLBACK') = (fallback_exam_attempt_id is not null))
);

create unique index uq_adaptive_placement_active_user
    on adaptive_placement_attempts (user_id) where status = 'IN_PROGRESS';

create table adaptive_placement_responses (
    attempt_id uuid not null references adaptive_placement_attempts (id) on delete restrict,
    ordinal integer not null check (ordinal > 0),
    item_id text not null references exam_items (item_id) on delete restrict,
    selected_label varchar(1) not null check (selected_label in ('A', 'B', 'C', 'D')),
    correct boolean not null,
    theta_before numeric(8, 5) not null,
    theta_after numeric(8, 5) not null,
    item_information numeric(12, 6) not null check (item_information >= 0),
    discrimination numeric(8, 5) not null,
    difficulty numeric(8, 5) not null,
    guessing numeric(8, 5) not null,
    calibration_version integer not null,
    idempotency_key varchar(200) not null,
    answered_at timestamptz not null default now(),
    primary key (attempt_id, ordinal),
    unique (attempt_id, item_id),
    unique (attempt_id, idempotency_key),
    foreign key (calibration_version, item_id)
        references irt_item_parameters (calibration_version, item_id) on delete restrict
);

create index idx_adaptive_placement_responses_item
    on adaptive_placement_responses (calibration_version, item_id);
