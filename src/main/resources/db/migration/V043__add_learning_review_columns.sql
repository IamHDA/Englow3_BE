-- The same review trail V042 gave papers, for the three kinds of practice
-- content. Identical columns, and deliberately so: one workflow means one shape,
-- and a per-type variation here would be a difference with no reason behind it.
--
-- The status columns are already varchar, so PENDING_REVIEW and REJECTED need no
-- schema change.

alter table flashcard_sets
    add column submitted_for_review_at timestamptz,
    add column reviewed_by_user_id uuid references users (id) on delete restrict,
    add column reviewed_at timestamptz,
    add column review_note text;

alter table quizzes
    add column submitted_for_review_at timestamptz,
    add column reviewed_by_user_id uuid references users (id) on delete restrict,
    add column reviewed_at timestamptz,
    add column review_note text;

alter table dictation_lessons
    add column submitted_for_review_at timestamptz,
    add column reviewed_by_user_id uuid references users (id) on delete restrict,
    add column reviewed_at timestamptz,
    add column review_note text;
