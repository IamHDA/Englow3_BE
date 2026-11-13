-- The IELTS full test (docs/decisions.md, D-IELTS-6 and D-IELTS-10): one Listening + Reading paper and five Writing
-- and Speaking tasks, sat as one test with one overall band. It is the IELTS learner's placement test and is also in
-- the catalogue. The exam module owns both tables; the five tasks belong to the assessment module and are referenced
-- by id only, never by foreign key.

create table full_tests (
    id                      uuid primary key,
    title                   varchar(100) not null,
    description             text         not null default '',
    certificate_variant     varchar(30)  not null check (certificate_variant in ('ACADEMIC', 'GENERAL')),
    exam_id                 uuid         not null references exams (id) on delete restrict,
    writing_task1_id        uuid         not null,
    writing_task2_id        uuid         not null,
    speaking_part1_id       uuid         not null,
    speaking_part2_id       uuid         not null,
    speaking_part3_id       uuid         not null,
    status                  varchar(20)  not null
        check (status in ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'PUBLISHED', 'ARCHIVED')),
    created_by_user_id      uuid         not null references users (id) on delete restrict,
    submitted_for_review_at timestamptz,
    reviewed_by_user_id     uuid references users (id) on delete set null,
    reviewed_at             timestamptz,
    review_note             text,
    published_at            timestamptz,
    version                 bigint       not null default 0,
    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now()
);

create index idx_full_tests_status on full_tests (status, created_at desc);
create index idx_full_tests_exam_id on full_tests (exam_id);

create trigger trg_full_tests_set_updated_at
before update on full_tests
for each row execute function set_updated_at();

-- One learner's sitting of a full test. Every part must be submitted within seven days of starting; the deadline
-- covers submitting only, so an answer still waiting for a grade counts once it is graded.
--   IN_PROGRESS      parts may still be taken
--   AWAITING_GRADES  every part is in, at least one is not graded yet
--   COMPLETED        every part graded; overall_band is set
--   EXPIRED          the seven days ran out with parts missing; the provisional band stays as it was
create table full_test_sittings (
    id                  uuid primary key,
    full_test_id        uuid         not null references full_tests (id) on delete restrict,
    user_id             uuid         not null references users (id) on delete cascade,
    exam_attempt_id     uuid references exam_attempts (id) on delete set null,
    writing1_attempt_id uuid,
    writing2_attempt_id uuid,
    speaking1_attempt_id uuid,
    speaking2_attempt_id uuid,
    speaking3_attempt_id uuid,
    listening_band      numeric(3, 1) check (listening_band between 0 and 9),
    reading_band        numeric(3, 1) check (reading_band between 0 and 9),
    writing_band        numeric(3, 1) check (writing_band between 0 and 9),
    speaking_band       numeric(3, 1) check (speaking_band between 0 and 9),
    provisional_band    numeric(3, 1) check (provisional_band between 0 and 9),
    overall_band        numeric(3, 1) check (overall_band between 0 and 9),
    status              varchar(20)  not null
        check (status in ('IN_PROGRESS', 'AWAITING_GRADES', 'COMPLETED', 'EXPIRED')),
    started_at          timestamptz  not null,
    deadline_at         timestamptz  not null,
    completed_at        timestamptz,
    version             bigint       not null default 0,
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),
    constraint ck_full_test_sitting_deadline check (deadline_at > started_at),
    constraint ck_full_test_sitting_overall check ((status = 'COMPLETED') = (overall_band is not null))
);

-- At most one open sitting per learner and full test: starting again resumes it.
create unique index uq_full_test_sittings_open
    on full_test_sittings (user_id, full_test_id) where status = 'IN_PROGRESS';
create index idx_full_test_sittings_user on full_test_sittings (user_id, started_at desc);
create index idx_full_test_sittings_unsettled on full_test_sittings (status, deadline_at)
    where status in ('IN_PROGRESS', 'AWAITING_GRADES');

create trigger trg_full_test_sittings_set_updated_at
before update on full_test_sittings
for each row execute function set_updated_at();
