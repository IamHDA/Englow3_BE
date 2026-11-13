alter table ai_jobs add column queue_scope varchar(60) not null default 'testing';
create index idx_ai_jobs_scope_pending on ai_jobs(queue_scope, job_type, next_retry_at, created_at) where status = 'PENDING';
create index idx_ai_jobs_scope_running on ai_jobs(queue_scope, lease_until) where status = 'RUNNING';
