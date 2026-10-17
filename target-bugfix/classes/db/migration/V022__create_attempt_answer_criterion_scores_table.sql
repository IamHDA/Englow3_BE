create table attempt_answer_criterion_scores (
    attempt_answer_id uuid not null references attempt_answers (id) on delete restrict,
    grading_criterion_id integer not null references grading_criteria (id) on delete restrict,
    score numeric(4, 1) not null,
    comment text,
    created_at timestamptz not null default now(),
    primary key (attempt_answer_id, grading_criterion_id)
);

create index idx_attempt_answer_criterion_scores_grading_criterion_id
    on attempt_answer_criterion_scores (grading_criterion_id);
