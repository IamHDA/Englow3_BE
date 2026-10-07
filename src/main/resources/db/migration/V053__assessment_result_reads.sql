create table assessment_result_reads (
    attempt_id uuid primary key references assessment_attempts(id) on delete cascade,
    result_version bigint not null check(result_version >= 0),
    read_at timestamptz not null
);
