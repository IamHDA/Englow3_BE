alter table speaking_sessions
    add column practice_id uuid,
    add column turn_number integer check (turn_number > 0),
    add column submit_idempotency_key varchar(200),
    add column audio_status varchar(20) not null default 'AVAILABLE'
        check (audio_status in ('AVAILABLE', 'DELETED')),
    add column audio_deleted_at timestamptz;

update speaking_sessions
set practice_id = id,
    turn_number = 1,
    audio_status = case when status = 'DELETED' then 'DELETED' else 'AVAILABLE' end,
    audio_deleted_at = deleted_at;

alter table speaking_sessions
    alter column practice_id set not null,
    alter column turn_number set not null;

create unique index uq_speaking_practice_turn
    on speaking_sessions (user_id, practice_id, turn_number);

create index idx_speaking_practice_created
    on speaking_sessions (user_id, practice_id, created_at);

create table speaking_phoneme_scores (
    session_id uuid not null references speaking_sessions (id) on delete cascade,
    word_position integer not null,
    position integer not null,
    phoneme varchar(100) not null,
    accuracy_score numeric(5, 2),
    primary key (session_id, word_position, position),
    foreign key (session_id, word_position) references speaking_word_scores (session_id, position)
        on delete cascade
);

create table speaking_error_aggregates (
    user_id uuid not null references users (id) on delete cascade,
    unit_type varchar(20) not null check (unit_type in ('WORD', 'PHONEME')),
    normalized_unit varchar(200) not null,
    error_type varchar(50) not null,
    occurrence_count integer not null check (occurrence_count > 0),
    average_accuracy numeric(5, 2),
    first_seen_at timestamptz not null,
    last_seen_at timestamptz not null,
    primary key (user_id, unit_type, normalized_unit, error_type)
);

create table speaking_error_observations (
    session_id uuid not null references speaking_sessions (id) on delete cascade,
    unit_type varchar(20) not null check (unit_type in ('WORD', 'PHONEME')),
    normalized_unit varchar(200) not null,
    error_type varchar(50) not null,
    accuracy numeric(5, 2),
    observed_at timestamptz not null default now(),
    primary key (session_id, unit_type, normalized_unit, error_type)
);

create index idx_speaking_errors_user_frequency
    on speaking_error_aggregates (user_id, occurrence_count desc, last_seen_at desc);

create table speaking_practice_recommendations (
    session_id uuid not null references speaking_sessions (id) on delete cascade,
    position integer not null,
    content_type varchar(30) not null check (content_type = 'SHADOWING_CLIP'),
    content_id text not null,
    reason varchar(500) not null,
    created_at timestamptz not null default now(),
    primary key (session_id, position),
    unique (session_id, content_type, content_id)
);
