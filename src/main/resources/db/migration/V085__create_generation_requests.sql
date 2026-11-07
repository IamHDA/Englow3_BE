-- AI content generation requests (docs/module-map.md, `generation`). One row per press of "generate": who asked, an
-- optional author's brief, which step is running, the components the model has written so far, and the drafts it
-- produced. The paper, full test and tasks belong to exam and assessment and are referenced by id only.

create table generation_requests (
    id                   uuid primary key,
    kind                 varchar(30)  not null check (kind in ('IELTS_FULL_TEST')),
    requested_by_user_id uuid         not null references users (id) on delete restrict,
    brief                varchar(1000),
    status               varchar(20)  not null check (status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    step                 varchar(30),
    step_index           integer      not null default 0,
    attempt              integer      not null default 0,
    components           jsonb        not null default '{}'::jsonb,
    assembly             jsonb,
    last_problems        jsonb,
    retry_context        jsonb,
    exam_id              uuid,
    task_ids             jsonb,
    full_test_id         uuid,
    failure_code         varchar(60),
    failure_message      varchar(2000),
    created_at           timestamptz  not null,
    updated_at           timestamptz  not null,
    completed_at         timestamptz,
    version              bigint       not null default 0
);

-- The daily limit and "one in progress" both read a user's requests of today.
create index generation_requests_user_created_idx on generation_requests (requested_by_user_id, created_at desc);
create index generation_requests_created_idx on generation_requests (created_at desc);
