create table ai_evaluation_suites (
    id uuid primary key,
    suite_key varchar(100) not null,
    version integer not null check (version > 0),
    capability varchar(40) not null,
    repetitions integer not null default 3 check (repetitions between 1 and 10),
    schema_success_min numeric(5, 4) not null default 1 check (schema_success_min between 0 and 1),
    evidence_fidelity_min numeric(5, 4) not null default 1 check (evidence_fidelity_min between 0 and 1),
    unsafe_rate_max numeric(5, 4) not null default 0 check (unsafe_rate_max between 0 and 1),
    score_variance_max numeric(12, 4) not null default 100 check (score_variance_max >= 0),
    human_agreement_min numeric(5, 4) not null default 0.8 check (human_agreement_min between 0 and 1),
    latency_p95_max_ms integer not null default 90000 check (latency_p95_max_ms > 0),
    suite_hash varchar(64) not null check (suite_hash ~ '^[0-9a-f]{64}$'),
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null default now(),
    unique (suite_key, version),
    unique (suite_hash)
);

create table ai_evaluation_cases (
    id uuid primary key,
    suite_id uuid not null references ai_evaluation_suites (id) on delete cascade,
    case_key varchar(100) not null,
    prompt_variables jsonb not null check (jsonb_typeof(prompt_variables) = 'object'),
    expected_contract jsonb not null check (jsonb_typeof(expected_contract) = 'object'),
    case_hash varchar(64) not null check (case_hash ~ '^[0-9a-f]{64}$'),
    created_at timestamptz not null default now(),
    unique (suite_id, case_key),
    unique (suite_id, case_hash)
);

create table ai_evaluation_candidates (
    id uuid primary key,
    suite_id uuid not null references ai_evaluation_suites (id) on delete restrict,
    provider_name varchar(50) not null,
    model_name varchar(100) not null,
    prompt_template_id uuid not null references ai_prompt_templates (id) on delete restrict,
    prompt_version integer not null check (prompt_version > 0),
    temperature numeric(3, 2) not null check (temperature between 0 and 2),
    max_output_tokens integer not null check (max_output_tokens between 1 and 32768),
    input_cost_per_million numeric(14, 6) not null default 0 check (input_cost_per_million >= 0),
    output_cost_per_million numeric(14, 6) not null default 0 check (output_cost_per_million >= 0),
    baseline_run_id uuid,
    candidate_hash varchar(64) not null check (candidate_hash ~ '^[0-9a-f]{64}$'),
    created_by uuid references users (id) on delete set null,
    created_at timestamptz not null default now(),
    unique (suite_id, candidate_hash),
    foreign key (prompt_template_id, prompt_version)
        references ai_prompt_versions (template_id, version)
);

create table ai_evaluation_runs (
    id uuid primary key,
    candidate_id uuid not null references ai_evaluation_candidates (id) on delete restrict,
    status varchar(30) not null check (status in
        ('PENDING', 'RUNNING', 'AWAITING_HUMAN', 'ACCEPTED', 'REJECTED', 'FAILED')),
    summary jsonb check (summary is null or jsonb_typeof(summary) = 'object'),
    hard_gates_passed boolean,
    human_quality_passed boolean,
    failure_code varchar(100),
    retry_count smallint not null default 0 check (retry_count between 0 and 3),
    decided_by uuid references users (id) on delete set null,
    decision_reason varchar(2000),
    started_at timestamptz,
    completed_at timestamptz,
    decided_at timestamptz,
    created_at timestamptz not null default now()
);

alter table ai_evaluation_candidates
    add constraint fk_ai_evaluation_candidate_baseline
    foreign key (baseline_run_id) references ai_evaluation_runs (id) on delete restrict;

create index idx_ai_evaluation_runs_status_created
    on ai_evaluation_runs (status, created_at);

create table ai_evaluation_case_results (
    run_id uuid not null references ai_evaluation_runs (id) on delete cascade,
    case_id uuid not null references ai_evaluation_cases (id) on delete restrict,
    attempt integer not null check (attempt > 0),
    schema_success boolean not null,
    evidence_fidelity numeric(5, 4) not null check (evidence_fidelity between 0 and 1),
    unsafe_response boolean not null,
    automatic_score numeric(8, 4),
    score_delta numeric(8, 4),
    latency_ms integer not null check (latency_ms >= 0),
    input_tokens integer not null check (input_tokens >= 0),
    output_tokens integer not null check (output_tokens >= 0),
    estimated_cost numeric(14, 6) not null check (estimated_cost >= 0),
    output_hash varchar(64) not null check (output_hash ~ '^[0-9a-f]{64}$'),
    violations jsonb not null check (jsonb_typeof(violations) = 'array'),
    created_at timestamptz not null default now(),
    primary key (run_id, case_id, attempt)
);

alter table ai_model_policies add column evaluation_run_id uuid references ai_evaluation_runs (id) on delete restrict;
alter table ai_prompt_versions add column evaluation_run_id uuid references ai_evaluation_runs (id) on delete restrict;
