-- Who asked for each job, so the daily provider budget can be counted across
-- every feature that spends it.
--
-- Speaking and the tutor each counted only their own work against the same
-- setting, which let one learner make twice the calls the limit was meant to
-- allow - and a third feature would have made it three times. Every provider
-- call already goes through this table, so this is the one place a total can
-- be counted without one module reading another's tables.
--
-- Nullable: rows written before this carry no requester, and a job the system
-- raises on its own behalf would not have one either. Set null rather than
-- cascade on delete, because the job is a record of spend and outlives the
-- account that caused it.

alter table ai_jobs
    add column requested_by_user_id uuid references users (id) on delete set null;

create index idx_ai_jobs_requested_by_user_id_created_at
    on ai_jobs (requested_by_user_id, created_at)
    where requested_by_user_id is not null;
