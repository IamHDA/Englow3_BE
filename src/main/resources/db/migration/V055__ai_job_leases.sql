ALTER TABLE englow3.ai_jobs ADD COLUMN lease_until timestamptz;
CREATE INDEX idx_ai_jobs_pending_type ON englow3.ai_jobs (job_type, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_ai_jobs_running_lease ON englow3.ai_jobs (lease_until)
    WHERE status = 'RUNNING';
