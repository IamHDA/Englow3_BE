-- Review workflow for papers: a staff author submits, an administrator approves
-- or rejects.
--
-- status is varchar, so PENDING_REVIEW and REJECTED need no schema change - only
-- the audit trail does. The reviewer and the timestamp are kept so a published
-- paper can answer "who approved this, and when". The note is kept because a
-- rejection without a reason tells the author nothing and leaves them nothing to
-- act on.

alter table exams
    add column submitted_for_review_at timestamptz,
    add column reviewed_by_user_id uuid references users (id) on delete restrict,
    add column reviewed_at timestamptz,
    add column review_note text;

-- The review queue is "everything waiting on me", so it filters by status alone.
-- exams had no status index; the learning tables index theirs already.
create index idx_exams_status on exams (status);
