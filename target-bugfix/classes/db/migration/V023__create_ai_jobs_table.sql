create table ai_jobs (
    id uuid primary key,
    job_type varchar(30) not null,
    target_type varchar(30) not null,
    target_id uuid not null,
    status varchar(20) not null,
    provider_name varchar(50) not null,
    model_name varchar(100) not null,
    prompt_version varchar(30) not null,
    input_payload jsonb not null,
    output_payload jsonb,
    idempotency_key varchar(255) not null unique,
    retry_count smallint not null default 0,
    max_retry_count smallint not null default 3,
    error_code varchar(100),
    error_message text,
    next_retry_at timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ai_jobs_status_next_retry_at on ai_jobs (status, next_retry_at);

create trigger trg_ai_jobs_set_updated_at
before update on ai_jobs
for each row execute function set_updated_at();
