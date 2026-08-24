create table learner_concept_mastery (
    user_id uuid not null references users (id) on delete cascade,
    concept_id text not null references concepts (concept_id) on delete cascade,
    probability_known real not null check (probability_known between 0 and 1),
    evidence_count integer not null default 0 check (evidence_count >= 0),
    last_practiced_at timestamptz,
    updated_at timestamptz not null default now(),
    primary key (user_id, concept_id)
);

create table learning_path_preferences (
    user_id uuid primary key references users (id) on delete cascade,
    daily_minutes integer not null default 20 check (daily_minutes between 5 and 240),
    items_per_path integer not null default 12 check (items_per_path between 3 and 30),
    updated_at timestamptz not null default now()
);

create trigger trg_learning_path_preferences_set_updated_at
before update on learning_path_preferences
for each row execute function set_updated_at();

create table learning_paths (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    status varchar(20) not null check (status in ('ACTIVE', 'SUPERSEDED', 'COMPLETED')),
    source varchar(30) not null default 'RULE_ENGINE',
    explanation text,
    ai_job_id uuid references ai_jobs (id) on delete set null,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_learning_paths_one_active
    on learning_paths (user_id) where status = 'ACTIVE';

create trigger trg_learning_paths_set_updated_at
before update on learning_paths
for each row execute function set_updated_at();

create table learning_path_items (
    id uuid primary key,
    learning_path_id uuid not null references learning_paths (id) on delete cascade,
    position integer not null check (position >= 1),
    concept_id text not null references concepts (concept_id) on delete restrict,
    reason varchar(500) not null,
    status varchar(20) not null default 'PENDING' check (status in ('PENDING', 'COMPLETED', 'SKIPPED')),
    completed_at timestamptz,
    unique (learning_path_id, position),
    unique (learning_path_id, concept_id)
);

create table learning_events (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    concept_id text not null references concepts (concept_id) on delete restrict,
    learning_path_item_id uuid references learning_path_items (id) on delete set null,
    event_type varchar(30) not null,
    successful boolean,
    source_type varchar(30) not null,
    source_id varchar(100),
    occurred_at timestamptz not null default now()
);

create index idx_learning_events_user_occurred on learning_events (user_id, occurred_at desc);

insert into ai_prompt_templates (id, template_key, description)
values ('10000000-0000-0000-0000-000000000130', 'LEARNING_PATH_EXPLANATION',
        'Explain a deterministic prerequisite-aware learning path')
on conflict (template_key) do nothing;

insert into ai_prompt_versions (
    id, template_id, version, system_template, user_template, response_schema, active
)
values (
    '10000000-0000-0000-0001-000000000130',
    '10000000-0000-0000-0000-000000000130',
    1,
    'You explain an English learning path in concise Vietnamese. The ordered concepts were selected by a prerequisite-aware rule engine. Do not add, remove, or reorder concepts. Return JSON with explanation and weeklyAdvice.',
    E'Learner level: {{level}}\nDaily minutes: {{dailyMinutes}}\nOrdered concepts:\n{{concepts}}',
    '{"type":"object","required":["explanation","weeklyAdvice"],"properties":{"explanation":{"type":"string"},"weeklyAdvice":{"type":"array","items":{"type":"string"}}}}'::jsonb,
    true
)
on conflict (template_id, version) do nothing;
