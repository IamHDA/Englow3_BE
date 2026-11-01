-- What the learning roadmap needs to know about a Writing/Speaking task: the CEFR band it is pitched at, whether it
-- follows an exam's format, and whether it is practice or the spoken part of the roadmap's level check.

alter table assessment_tasks
    add column target_level varchar(2) check (target_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    add column purpose varchar(20) not null default 'PRACTICE' check (purpose in ('PRACTICE', 'LEVEL_CHECK')),
    add column certificate_type varchar(10) check (certificate_type in ('IELTS', 'TOEIC')),
    -- The level check measures speaking; its other parts are an exam the exam module grades.
    add constraint ck_assessment_task_level_check_speaking check (purpose <> 'LEVEL_CHECK' or skill = 'SPEAKING');

create index idx_assessment_tasks_purpose on assessment_tasks (purpose, skill, status);

-- Turns a graded spoken answer into a CEFR band. The overall score is the mean of four criteria on the IELTS 0-9
-- scale (computed by the server, in half bands), so the published IELTS-to-CEFR alignment is the starting point.
-- Below 4.0 IELTS does not discriminate well, so those two rows are estimates. A new row set with a later
-- effective_from replaces the mapping without changing levels already recorded.
create table speaking_level_conversions (
    id             uuid primary key,
    min_overall    numeric(3, 1) not null check (min_overall between 0 and 9),
    max_overall    numeric(3, 1) not null check (max_overall between 0 and 9),
    cefr_level     varchar(2)    not null check (cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    effective_from timestamptz   not null default now(),
    created_at     timestamptz   not null default now(),
    constraint ck_speaking_level_range check (min_overall <= max_overall),
    constraint uq_speaking_level_from unique (effective_from, min_overall)
);

insert into speaking_level_conversions (id, min_overall, max_overall, cefr_level, effective_from) values
    ('0b8c2f9e-6c1a-4d8e-9f00-000000000001', 0.0, 2.5, 'A1', '2026-01-01T00:00:00Z'),
    ('0b8c2f9e-6c1a-4d8e-9f00-000000000002', 3.0, 3.5, 'A2', '2026-01-01T00:00:00Z'),
    ('0b8c2f9e-6c1a-4d8e-9f00-000000000003', 4.0, 5.0, 'B1', '2026-01-01T00:00:00Z'),
    ('0b8c2f9e-6c1a-4d8e-9f00-000000000004', 5.5, 6.5, 'B2', '2026-01-01T00:00:00Z'),
    ('0b8c2f9e-6c1a-4d8e-9f00-000000000005', 7.0, 8.0, 'C1', '2026-01-01T00:00:00Z'),
    ('0b8c2f9e-6c1a-4d8e-9f00-000000000006', 8.5, 9.0, 'C2', '2026-01-01T00:00:00Z');
