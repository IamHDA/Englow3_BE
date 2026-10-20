create table tutor_recordings (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    client_message_id uuid not null,
    object_key varchar(500) not null,
    audio_size bigint not null,
    fingerprint varchar(64) not null,
    locale varchar(10) not null,
    status varchar(20) not null,
    transcript text,
    error_code varchar(100),
    created_at timestamptz not null default now(),
    unique(user_id, client_message_id)
);
create index idx_tutor_recording_cleanup on tutor_recordings(status, created_at);
