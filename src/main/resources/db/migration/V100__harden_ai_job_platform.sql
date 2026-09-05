alter table ai_jobs
    add column requester_user_id uuid references users (id) on delete set null,
    add column capability varchar(40),
    add column available_at timestamptz not null default now(),
    add column locked_at timestamptz,
    add column locked_by varchar(100),
    add column trace_id varchar(64),
    add column input_tokens integer not null default 0,
    add column output_tokens integer not null default 0,
    add column estimated_cost numeric(14, 6) not null default 0,
    add column version bigint not null default 0;

update ai_jobs set capability = job_type where capability is null;
alter table ai_jobs alter column capability set not null;

alter table ai_jobs drop constraint if exists ai_jobs_idempotency_key_key;
create unique index uq_ai_jobs_requester_idempotency
    on ai_jobs (requester_user_id, idempotency_key)
    where requester_user_id is not null;
create unique index uq_ai_jobs_system_idempotency
    on ai_jobs (idempotency_key)
    where requester_user_id is null;
drop index if exists idx_ai_jobs_status_next_retry_at;
create index idx_ai_jobs_claim
    on ai_jobs (status, available_at, created_at)
    where status in ('PENDING', 'RETRY_SCHEDULED');

create table ai_prompt_templates (
    id uuid primary key,
    template_key varchar(100) not null unique,
    description varchar(500) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_ai_prompt_templates_set_updated_at
before update on ai_prompt_templates
for each row execute function set_updated_at();

create table ai_prompt_versions (
    id uuid primary key,
    template_id uuid not null references ai_prompt_templates (id) on delete cascade,
    version integer not null,
    system_template text not null,
    user_template text not null,
    response_schema jsonb,
    active boolean not null default false,
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null default now(),
    unique (template_id, version)
);

create unique index uq_ai_prompt_versions_one_active
    on ai_prompt_versions (template_id)
    where active;

create table ai_model_policies (
    capability varchar(40) primary key,
    provider_name varchar(50) not null,
    model_name varchar(100) not null,
    temperature numeric(3, 2) not null default 0.2,
    max_output_tokens integer not null default 2048,
    enabled boolean not null default true,
    updated_at timestamptz not null default now(),
    check (temperature >= 0 and temperature <= 2),
    check (max_output_tokens between 1 and 32768)
);

create trigger trg_ai_model_policies_set_updated_at
before update on ai_model_policies
for each row execute function set_updated_at();

create table ai_usage_daily (
    user_id uuid not null references users (id) on delete cascade,
    usage_date date not null,
    request_count integer not null default 0,
    input_tokens bigint not null default 0,
    output_tokens bigint not null default 0,
    estimated_cost numeric(14, 6) not null default 0,
    primary key (user_id, usage_date),
    check (request_count >= 0),
    check (input_tokens >= 0),
    check (output_tokens >= 0),
    check (estimated_cost >= 0)
);
