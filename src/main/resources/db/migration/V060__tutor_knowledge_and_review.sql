create table tutor_resources (
    id uuid primary key,
    document_id uuid not null,
    version_number integer not null check(version_number > 0),
    title varchar(200) not null,
    body text not null,
    status varchar(20) not null default 'DRAFT',
    author_id uuid not null references users(id),
    review_note varchar(2000),
    row_version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(document_id, version_number)
);
create unique index idx_tutor_one_published_resource on tutor_resources(document_id) where status = 'PUBLISHED';
create index idx_tutor_resource_search on tutor_resources using gin(to_tsvector('simple', title || ' ' || body)) where status = 'PUBLISHED';
create trigger trg_tutor_resources_updated before update on tutor_resources for each row execute function set_updated_at();

create table tutor_reports (
    id uuid primary key,
    message_id uuid not null references tutor_messages(id) on delete cascade,
    revision integer not null,
    reporter_id uuid not null references users(id) on delete cascade,
    question_snapshot text not null,
    answer_snapshot text not null,
    learner_note varchar(2000),
    status varchar(20) not null default 'OPEN',
    assigned_to uuid references users(id),
    reviewer_note varchar(2000),
    proposed_correction text,
    created_at timestamptz not null default now(),
    resolved_at timestamptz,
    unique(message_id, revision, reporter_id)
);
create index idx_tutor_report_assignee on tutor_reports(assigned_to, status, created_at desc);
create index idx_tutor_report_status on tutor_reports(status, created_at desc);

create table tutor_audit (
    id uuid primary key,
    actor_id uuid not null references users(id),
    target_id uuid not null,
    action varchar(60) not null,
    note varchar(2000),
    created_at timestamptz not null default now()
);
create index idx_tutor_audit_target on tutor_audit(target_id, created_at desc);

alter table tutor_messages add column correction text;
alter table tutor_messages add column report_status varchar(20);
alter table tutor_messages add column sources_json jsonb not null default '[]';
