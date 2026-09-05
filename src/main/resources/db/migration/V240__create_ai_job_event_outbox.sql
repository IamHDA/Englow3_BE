create table ai_job_events (
    event_id bigserial primary key,
    job_id uuid not null references ai_jobs (id) on delete cascade,
    requester_user_id uuid references users (id) on delete cascade,
    capability varchar(40) not null,
    event_type varchar(30) not null check (event_type in
        ('QUEUED', 'PROCESSING', 'RETRY_SCHEDULED', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    retry_count smallint not null default 0 check (retry_count >= 0),
    payload jsonb not null check (jsonb_typeof(payload) = 'object'),
    delivery_count integer not null default 0 check (delivery_count >= 0),
    last_delivered_at timestamptz,
    created_at timestamptz not null default now(),
    unique (job_id, event_type, retry_count)
);

create index idx_ai_job_events_user_cursor
    on ai_job_events (requester_user_id, event_id);

create table ai_notifications (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    job_id uuid not null references ai_jobs (id) on delete cascade,
    event_id bigint not null unique references ai_job_events (event_id) on delete cascade,
    notification_type varchar(30) not null check (notification_type in ('AI_JOB_SUCCEEDED', 'AI_JOB_FAILED')),
    target_type varchar(30) not null,
    target_id uuid not null,
    read_at timestamptz,
    created_at timestamptz not null default now(),
    unique (user_id, job_id, notification_type)
);

create index idx_ai_notifications_user_unread
    on ai_notifications (user_id, created_at desc) where read_at is null;
