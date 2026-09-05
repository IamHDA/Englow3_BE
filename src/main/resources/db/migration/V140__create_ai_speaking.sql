create table speaking_sessions (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    mode varchar(30) not null check (mode in ('READ_ALOUD', 'FREE_SPEAKING')),
    locale varchar(20) not null,
    reference_text text,
    audio_bucket varchar(100) not null,
    audio_object_key varchar(500) not null unique,
    audio_content_type varchar(100) not null,
    audio_size_bytes bigint,
    status varchar(30) not null check (status in
        ('AWAITING_UPLOAD', 'PROCESSING', 'COMPLETED', 'FAILED', 'DELETED')),
    ai_job_id uuid references ai_jobs (id) on delete set null,
    consented_at timestamptz not null,
    retention_until timestamptz not null,
    submitted_at timestamptz,
    completed_at timestamptz,
    deleted_at timestamptz,
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    check (mode <> 'READ_ALOUD' or reference_text is not null)
);

create index idx_speaking_sessions_user_created on speaking_sessions (user_id, created_at desc);
create index idx_speaking_sessions_retention on speaking_sessions (retention_until)
    where status <> 'DELETED';

create trigger trg_speaking_sessions_set_updated_at
before update on speaking_sessions
for each row execute function set_updated_at();

create table speaking_assessments (
    session_id uuid primary key references speaking_sessions (id) on delete cascade,
    recognized_text text not null,
    accuracy_score numeric(5, 2),
    fluency_score numeric(5, 2),
    completeness_score numeric(5, 2),
    prosody_score numeric(5, 2),
    pronunciation_score numeric(5, 2),
    grammar_feedback text,
    vocabulary_feedback text,
    provider_name varchar(50) not null,
    provider_request_id varchar(200),
    raw_result jsonb not null,
    created_at timestamptz not null default now()
);

create table speaking_word_scores (
    session_id uuid not null references speaking_sessions (id) on delete cascade,
    position integer not null,
    word varchar(200) not null,
    accuracy_score numeric(5, 2),
    error_type varchar(50),
    offset_ms integer,
    duration_ms integer,
    primary key (session_id, position)
);

insert into ai_prompt_templates (id, template_key, description)
values ('10000000-0000-0000-0000-000000000140', 'SPEAKING_LANGUAGE_FEEDBACK',
        'Grammar and vocabulary feedback for a speech transcript')
on conflict (template_key) do nothing;

insert into ai_prompt_versions (
    id, template_id, version, system_template, user_template, response_schema, active
)
values (
    '10000000-0000-0000-0001-000000000140',
    '10000000-0000-0000-0000-000000000140',
    1,
    'You are an English speaking coach. Pronunciation scores were produced by a speech assessment engine and must not be changed. Analyze only grammar and vocabulary in the recognized transcript. Return concise JSON with grammarFeedback and vocabularyFeedback.',
    E'Recognized transcript:\n{{transcript}}\n\nPronunciation summary:\n{{scores}}',
    '{"type":"object","required":["grammarFeedback","vocabularyFeedback"],"properties":{"grammarFeedback":{"type":"string"},"vocabularyFeedback":{"type":"string"}}}'::jsonb,
    true
)
on conflict (template_id, version) do nothing;
