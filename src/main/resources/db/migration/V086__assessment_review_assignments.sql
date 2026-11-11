alter table assessment_attempts add column assigned_reviewer_id uuid references users(id) on delete set null;
create index idx_assessment_assigned_queue on assessment_attempts(assigned_reviewer_id, status, submitted_at, id)
    where status <> 'DRAFT';
create index idx_assessment_unassigned_queue on assessment_attempts(task_id, status, submitted_at, id)
    where assigned_reviewer_id is null and status <> 'DRAFT';

create table assessment_assignments (
    id uuid primary key,
    attempt_id uuid not null references assessment_attempts(id) on delete cascade,
    assigned_by_user_id uuid references users(id) on delete set null,
    reviewer_id uuid references users(id) on delete set null,
    note varchar(2000) not null,
    created_at timestamptz not null
);
create index idx_assessment_assignments_attempt on assessment_assignments(attempt_id, created_at desc);
