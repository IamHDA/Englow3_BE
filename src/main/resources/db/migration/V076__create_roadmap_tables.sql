-- The learning roadmap: a plan, per learner, of what to do to reach their goal - stages, the content in each, and the
-- test that closes each stage. Only the plan is stored. Progress on an item, XP, streaks and quests are still derived
-- from activity, so nothing here can drift from what the learner actually did.
--
-- Owned by the roadmap module. Content and attempts are referenced by id only; they belong to their own modules.

create table learning_roadmaps (
    id                 uuid primary key,
    user_id            uuid         not null references users (id) on delete cascade,
    status             varchar(20)  not null
        check (status in ('GENERATING', 'ACTIVE', 'FAILED', 'COMPLETED', 'SUPERSEDED', 'ABANDONED')),
    goal_type          varchar(20)  not null check (goal_type in ('CERTIFICATE', 'LEVEL')),
    target_certificate varchar(10) check (target_certificate in ('IELTS', 'TOEIC')),
    target_score       numeric(5, 1),
    target_level       varchar(2) check (target_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    target_date        date,
    start_level        varchar(2)   not null check (start_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    start_score        numeric(5, 1),
    daily_minutes      integer check (daily_minutes between 10 and 240),
    model_name         varchar(120),
    prompt_version     varchar(40),
    failure_code       varchar(100),
    -- The generation or adjustment in flight. Its job carries the same token, so a late result for a request that was
    -- replaced (the learner rebuilt the plan meanwhile) is recognised and dropped rather than applied.
    pending_job_token  uuid,
    pending_reason     varchar(30),
    version            bigint       not null default 0,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    completed_at       timestamptz,
    -- A plan aims at one finishing line: a certificate score or a CEFR level.
    constraint ck_roadmap_goal check (
        (goal_type = 'CERTIFICATE' and target_certificate is not null and target_score is not null
            and target_level is null)
        or (goal_type = 'LEVEL' and target_level is not null and target_certificate is null and target_score is null))
);

-- At most one plan in play per learner. Finished, failed and replaced plans are kept as history.
create unique index uq_learning_roadmaps_one_open
    on learning_roadmaps (user_id) where status in ('GENERATING', 'ACTIVE');

create table roadmap_stages (
    id            uuid primary key,
    roadmap_id    uuid         not null references learning_roadmaps (id) on delete cascade,
    order_no      integer      not null check (order_no > 0),
    title         varchar(200) not null,
    summary       text         not null default '',
    from_level    varchar(2)   not null check (from_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    to_level      varchar(2)   not null check (to_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    focus_skills  varchar(200) not null default '',
    planned_start date         not null,
    planned_end   date         not null,
    status        varchar(20)  not null check (status in ('LOCKED', 'CURRENT', 'PASSED', 'REPLACED')),
    created_at    timestamptz  not null default now(),
    constraint ck_roadmap_stage_dates check (planned_start <= planned_end)
);

-- An adjustment marks stages REPLACED and inserts new ones at the same positions, so order is unique only among the
-- stages still in force.
create unique index uq_roadmap_stages_order
    on roadmap_stages (roadmap_id, order_no) where status <> 'REPLACED';
create unique index uq_roadmap_stages_one_current
    on roadmap_stages (roadmap_id) where status = 'CURRENT';

create table roadmap_items (
    id             uuid primary key,
    stage_id       uuid         not null references roadmap_stages (id) on delete cascade,
    order_no       integer      not null check (order_no > 0),
    content_kind   varchar(30)  not null
        check (content_kind in ('FLASHCARD_SET', 'DICTATION_LESSON', 'QUIZ', 'SPEAKING_PROMPT', 'ASSESSMENT_TASK')),
    content_id     uuid         not null,
    -- The title when the plan was made, so a plan still reads sensibly if the content is later renamed or archived.
    title          varchar(200) not null,
    -- What the content trains (GRAMMAR, LISTENING, WRITING, ...), as the catalogue described it when planned.
    skill          varchar(30),
    required       boolean      not null default true,
    target_percent integer      not null default 80 check (target_percent between 1 and 100),
    planned_week   integer      not null default 1 check (planned_week > 0),
    estimated_minutes integer   not null default 15 check (estimated_minutes between 1 and 240),
    unique (stage_id, order_no)
);

create index idx_roadmap_items_content on roadmap_items (content_kind, content_id);

create table roadmap_milestones (
    id                     uuid primary key,
    stage_id               uuid         not null unique references roadmap_stages (id) on delete cascade,
    kind                   varchar(20)  not null check (kind in ('CERTIFICATE_MOCK', 'LEVEL_CHECK')),
    exam_id                uuid,
    required_score         numeric(5, 1),
    required_level         varchar(2) check (required_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    result_exam_attempt_id uuid,
    result_level_check_id  uuid,
    result_score           numeric(5, 1),
    result_level           varchar(2) check (result_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    attempt_count          integer      not null default 0,
    status                 varchar(20)  not null check (status in ('PENDING', 'PASSED', 'FAILED')),
    achieved_at            timestamptz,
    updated_at             timestamptz  not null default now(),
    constraint ck_roadmap_milestone_kind check (
        (kind = 'CERTIFICATE_MOCK' and exam_id is not null and required_score is not null)
        or (kind = 'LEVEL_CHECK' and required_level is not null))
);

-- One measurement of a learner's level by the three-part test: an exam (vocabulary/grammar as its READING section,
-- plus LISTENING) and a spoken answer. Taken at onboarding, before any roadmap exists, or to close a stage.
create table level_checks (
    id                  uuid primary key,
    user_id             uuid        not null references users (id) on delete cascade,
    roadmap_id          uuid references learning_roadmaps (id) on delete cascade,
    milestone_id        uuid references roadmap_milestones (id) on delete cascade,
    purpose             varchar(20) not null check (purpose in ('ONBOARDING', 'MILESTONE')),
    exam_id             uuid        not null,
    speaking_task_id    uuid,
    exam_attempt_id     uuid,
    speaking_attempt_id uuid,
    speaking_skipped    boolean     not null default false,
    vocab_level         varchar(2) check (vocab_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    listening_level     varchar(2) check (listening_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    speaking_level      varchar(2) check (speaking_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    result_level        varchar(2) check (result_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    weights             jsonb,
    status              varchar(20) not null check (status in ('IN_PROGRESS', 'AWAITING_SPEAKING', 'COMPLETED')),
    version             bigint      not null default 0,
    created_at          timestamptz not null default now(),
    completed_at        timestamptz,
    constraint ck_level_check_milestone check ((purpose = 'MILESTONE') = (milestone_id is not null))
);

create index idx_level_checks_user on level_checks (user_id, created_at desc);
create unique index uq_level_checks_exam_attempt on level_checks (exam_attempt_id) where exam_attempt_id is not null;

create table roadmap_adjustments (
    id         uuid primary key,
    roadmap_id uuid        not null references learning_roadmaps (id) on delete cascade,
    reason     varchar(30) not null check (reason in ('MILESTONE_PASSED', 'MILESTONE_FAILED', 'BEHIND_SCHEDULE',
                                                      'AHEAD_OF_SCHEDULE', 'GOAL_CHANGED', 'LEVEL_REASSESSED')),
    before     jsonb       not null,
    after      jsonb,
    created_at timestamptz not null default now()
);

create index idx_roadmap_adjustments_roadmap on roadmap_adjustments (roadmap_id, created_at desc);

-- A section of the level check as a CEFR band, by the share of its points earned. Percentages rather than raw scores
-- because level-check papers may differ in length, and rather than score_conversions because that table is keyed by a
-- certificate, which a level check does not have. Seeded with the bands the placement test already uses
-- (user.PlacementRecorderImpl), so the two measurements agree; a later effective_from replaces them without changing
-- results already recorded.
create table level_check_conversions (
    id             uuid primary key,
    section_type   varchar(20)  not null check (section_type in ('READING', 'LISTENING')),
    min_percent    numeric(5, 2) not null check (min_percent between 0 and 100),
    cefr_level     varchar(2)   not null check (cefr_level in ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    effective_from timestamptz  not null default now(),
    unique (section_type, effective_from, min_percent)
);

insert into level_check_conversions (id, section_type, min_percent, cefr_level, effective_from)
select gen_random_uuid(), section, band.min_percent, band.level, '2026-01-01T00:00:00Z'
  from (values ('READING'), ('LISTENING')) sections(section)
 cross join (values (0, 'A1'), (25, 'A2'), (45, 'B1'), (60, 'B2'), (75, 'C1'), (90, 'C2')) band(min_percent, level);
