-- The platform-wide daily AI ceiling counts every job a queue scope created today. Without this index that count reads
-- the whole table on each request; with it, only today's rows.
create index idx_ai_jobs_scope_created_at on ai_jobs (queue_scope, created_at);
