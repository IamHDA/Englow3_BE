create table placement_scoring_bands (
    policy_version integer not null,
    cefr_level varchar(2) not null check (cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    minimum_percentage numeric(5, 2) not null check (minimum_percentage between 0 and 100),
    primary key (policy_version, cefr_level),
    unique (policy_version, minimum_percentage)
);

insert into placement_scoring_bands (policy_version, cefr_level, minimum_percentage)
values (1, 'A1', 0), (1, 'A2', 30), (1, 'B1', 45), (1, 'B2', 60), (1, 'C1', 75)
on conflict do nothing;

create table placement_ai_reports (
    id uuid primary key,
    exam_attempt_id uuid not null unique references exam_attempts (id) on delete cascade,
    ai_job_id uuid references ai_jobs (id) on delete set null,
    scoring_policy_version integer not null,
    summary text,
    strengths jsonb,
    learning_gaps jsonb,
    model_name varchar(100),
    prompt_version varchar(30),
    created_at timestamptz not null default now(),
    completed_at timestamptz
);

insert into ai_prompt_templates (id, template_key, description)
values ('10000000-0000-0000-0000-000000000120', 'PLACEMENT_REPORT',
        'Explain a deterministic placement result without changing its score')
on conflict (template_key) do nothing;

insert into ai_prompt_versions (
    id, template_id, version, system_template, user_template, response_schema, active
)
values (
    '10000000-0000-0000-0001-000000000120',
    '10000000-0000-0000-0000-000000000120',
    1,
    'You explain an English placement result. The assessed CEFR level and numeric scores are final deterministic results and must never be changed. Give concise, supportive, actionable feedback in Vietnamese. Return JSON with summary, strengths, and learningGaps.',
    E'Assessed level: {{level}}\nScore: {{score}}/{{maxScore}} ({{percentage}}%)\nSkill results:\n{{skills}}',
    '{"type":"object","required":["summary","strengths","learningGaps"],"properties":{"summary":{"type":"string"},"strengths":{"type":"array","items":{"type":"string"}},"learningGaps":{"type":"array","items":{"type":"string"}}}}'::jsonb,
    true
)
on conflict (template_id, version) do nothing;
