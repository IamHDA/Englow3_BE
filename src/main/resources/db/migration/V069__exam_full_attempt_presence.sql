-- A full sitting is the real test, and leaving it ends it. The browser says it is still there while it is; a sitting
-- it has not heard from for a grace period is abandoned, and the deadline worker finalizes it from what was saved.
--
-- Null for older rows and for a backend from before this migration, which does not set it while a deploy rolls over:
-- the application reads null as the moment the attempt started.
alter table exam_attempts
    add column last_seen_at timestamptz;

-- Sittings open at the moment of the migration get a fresh grace period rather than being ended on the spot.
update exam_attempts
   set last_seen_at = now()
 where status = 'IN_PROGRESS' and mode = 'FULL';
