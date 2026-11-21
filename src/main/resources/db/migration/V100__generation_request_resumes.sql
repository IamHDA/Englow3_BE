-- A failed generation request can be resumed from the step it stopped at, keeping what the model already wrote. The
-- count bounds how often one request is resumed, since every resume can spend provider calls again, and goes into the
-- idempotency key of the step's job so a resumed step is a new job rather than the failed one.

alter table generation_requests
    add column resume_count integer not null default 0 check (resume_count >= 0);
