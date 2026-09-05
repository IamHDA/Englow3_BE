create table ai_content_drafts (
    id uuid primary key,
    created_by uuid not null references users (id) on delete restrict,
    content_type varchar(30) not null check (content_type in ('QUIZ', 'DICTATION', 'FLASHCARDS', 'GRAMMAR_LESSON')),
    title varchar(200) not null,
    level varchar(10) not null,
    generation_request jsonb not null,
    generated_content jsonb,
    status varchar(30) not null check (status in
        ('GENERATING', 'DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED', 'FAILED')),
    ai_job_id uuid references ai_jobs (id) on delete set null,
    idempotency_key varchar(200) not null,
    prompt_version varchar(30) not null,
    reviewed_by uuid references users (id) on delete set null,
    review_reason varchar(1000),
    reviewed_at timestamptz,
    published_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index uq_ai_content_drafts_creator_idempotency
    on ai_content_drafts (created_by, idempotency_key);

create index idx_ai_content_drafts_status_created on ai_content_drafts (status, created_at desc);
create index idx_ai_content_drafts_creator on ai_content_drafts (created_by, created_at desc);

create trigger trg_ai_content_drafts_set_updated_at
before update on ai_content_drafts
for each row execute function set_updated_at();

create table ai_feedback_reports (
    id uuid primary key,
    reporter_user_id uuid not null references users (id) on delete cascade,
    ai_job_id uuid references ai_jobs (id) on delete set null,
    capability varchar(40) not null,
    category varchar(30) not null check (category in
        ('INCORRECT', 'INAPPROPRIATE', 'UNSAFE', 'LOW_QUALITY', 'OTHER')),
    details varchar(2000),
    status varchar(20) not null default 'OPEN' check (status in ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED')),
    resolution varchar(2000),
    resolved_by uuid references users (id) on delete set null,
    resolved_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_ai_feedback_reports_status_created on ai_feedback_reports (status, created_at desc);

create trigger trg_ai_feedback_reports_set_updated_at
before update on ai_feedback_reports
for each row execute function set_updated_at();

create table ai_admin_audit_log (
    id uuid primary key,
    actor_user_id uuid references users (id) on delete set null,
    action varchar(100) not null,
    target_type varchar(80) not null,
    target_id varchar(100) not null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index idx_ai_admin_audit_created on ai_admin_audit_log (created_at desc);

insert into ai_prompt_templates (id, template_key, description)
values ('10000000-0000-0000-0000-000000000150', 'CONTENT_DRAFT_GENERATION',
        'Generate staff-editable English learning content drafts')
on conflict (template_key) do nothing;

insert into ai_prompt_versions (
    id, template_id, version, system_template, user_template, response_schema, active
)
values (
    '10000000-0000-0000-0001-000000000150',
    '10000000-0000-0000-0000-000000000150',
    1,
    'You create English learning material for staff review. Never claim the draft is approved. Use the requested CEFR level, avoid unsafe or discriminatory content, provide unambiguous answers and concise explanations. Return valid JSON only.',
    E'Type: {{contentType}}\nTitle: {{title}}\nCEFR: {{level}}\nItem count: {{itemCount}}\nInstructions: {{instructions}}',
    '{"type":"object","required":["title","items"],"properties":{"title":{"type":"string"},"items":{"type":"array"}}}'::jsonb,
    true
)
on conflict (template_id, version) do nothing;
