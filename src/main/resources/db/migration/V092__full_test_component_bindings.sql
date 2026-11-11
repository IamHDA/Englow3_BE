create table full_test_component_attempts (
    id uuid primary key,
    sitting_id uuid not null references full_test_sittings(id) on delete cascade,
    slot varchar(30) not null check (slot in ('PAPER','WRITING_TASK_1','WRITING_TASK_2','SPEAKING_PART_1','SPEAKING_PART_2','SPEAKING_PART_3')),
    attempt_id uuid unique,
    unique(sitting_id, slot)
);
-- Preserve explicit links in existing results. Shared legacy evidence is retained on its original
-- result rows; only its earliest claim is registered, and new work never uses temporal inference.
insert into full_test_component_attempts(id,sitting_id,slot,attempt_id)
select gen_random_uuid(), id, slot, attempt_id from (
    select s.id, v.slot, v.attempt_id,
           row_number() over(partition by v.attempt_id order by s.started_at,s.id) as claim
    from full_test_sittings s cross join lateral (values
      ('PAPER',s.exam_attempt_id),('WRITING_TASK_1',s.writing1_attempt_id),
      ('WRITING_TASK_2',s.writing2_attempt_id),('SPEAKING_PART_1',s.speaking1_attempt_id),
      ('SPEAKING_PART_2',s.speaking2_attempt_id),('SPEAKING_PART_3',s.speaking3_attempt_id)
    ) v(slot,attempt_id) where v.attempt_id is not null
) claims where claim=1;
alter table full_test_sittings add column band_revision integer not null default 0,
    add column revised_at timestamptz,
    add column last_reconciled_at timestamptz;
create index idx_full_test_reconcile on full_test_sittings(last_reconciled_at nulls first, id)
    where status in ('IN_PROGRESS','AWAITING_GRADES');
create table full_test_band_revisions (
    id uuid primary key,
    sitting_id uuid not null references full_test_sittings(id) on delete cascade,
    revision integer not null,
    previous_bands jsonb not null,
    corrected_bands jsonb not null,
    created_at timestamptz not null,
    unique(sitting_id,revision)
);
alter table assessment_attempts add column recording_limit_seconds integer
    check(recording_limit_seconds between 1 and 300);
