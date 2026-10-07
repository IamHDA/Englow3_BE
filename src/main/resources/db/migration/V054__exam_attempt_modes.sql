-- Two ways to sit a paper. FULL is the real thing: every part, the paper's own clock, and the only kind that counts
-- toward progress, the best score and placement. PRACTICE is a chosen subset of parts with a clock the learner picks,
-- or none; it is kept in history and nowhere else.
alter table exam_attempts
    add column mode varchar(16) not null default 'FULL' check (mode in ('FULL', 'PRACTICE'));

-- The clock this attempt runs on. Null only for an untimed practice, which still has an expires_at - a generous one,
-- so an abandoned practice is finalized by the deadline worker like any other attempt instead of staying open forever.
alter table exam_attempts
    add column time_limit_seconds integer check (time_limit_seconds is null or time_limit_seconds > 0);

-- No check tying FULL to a clock: a backend from before this migration still inserts attempts without the column, and
-- it must keep working while a deploy rolls over. The application always sets it for a full attempt.
update exam_attempts a
   set time_limit_seconds = greatest(1, extract(epoch from (a.expires_at - a.started_at))::integer);

-- The parts a practice covers. A full attempt has no rows here: it covers the whole paper by definition, and listing
-- every part would only be a second copy of the paper's structure to keep in step.
create table exam_attempt_parts (
    exam_attempt_id uuid not null references exam_attempts (id) on delete cascade,
    section_part_id uuid not null references section_parts (id) on delete restrict,
    primary key (exam_attempt_id, section_part_id)
);

create index idx_exam_attempt_parts_part on exam_attempt_parts (section_part_id);
