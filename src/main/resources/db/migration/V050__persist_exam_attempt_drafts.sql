create table exam_attempt_drafts (
    attempt_id uuid primary key references exam_attempts(id),
    answers jsonb not null default '[]'::jsonb check (jsonb_typeof(answers) = 'array'),
    revision bigint not null default 0 check (revision >= 0),
    saved_at timestamptz not null
);
create index idx_exam_attempt_deadline on exam_attempts(expires_at) where status = 'IN_PROGRESS';
