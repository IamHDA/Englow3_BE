create table assessment_tasks (
 id uuid primary key, skill varchar(20) not null check (skill in ('WRITING','SPEAKING')),
 title varchar(200) not null, task_type varchar(30) not null,
 instructions text not null, rubric_notes text not null default '', sample_answer text not null default '',
 minimum_words integer not null default 0 check (minimum_words between 0 and 1000),
 time_limit_seconds integer not null check (time_limit_seconds between 30 and 3600),
 status varchar(30) not null check (status in ('DRAFT','PENDING_REVIEW','REJECTED','PUBLISHED','ARCHIVED')),
 created_by_user_id uuid not null references users(id), review_note text,
 reviewed_by_user_id uuid references users(id), published_at timestamptz,
 version bigint not null default 0, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index idx_assessment_tasks_catalog on assessment_tasks(skill,status,created_at desc);
create index idx_assessment_tasks_author on assessment_tasks(created_by_user_id,status);
create trigger trg_assessment_tasks_updated before update on assessment_tasks for each row execute function set_updated_at();

create table assessment_attempts (
 id uuid primary key, user_id uuid not null references users(id), task_id uuid not null references assessment_tasks(id),
 client_key uuid not null, skill varchar(20) not null check (skill in ('WRITING','SPEAKING')),
 task_snapshot jsonb not null, answer_text text not null default '',
 audio_object_key varchar(500), audio_content_type varchar(100), audio_content_length bigint,
 status varchar(30) not null check (status in ('DRAFT','QUEUED','NEEDS_REVIEW','COMPLETED','FAILED')),
 report jsonb, recognized_text text, source varchar(20), error_code varchar(100),
 grading_revision integer not null default 0, version bigint not null default 0,
 created_at timestamptz not null default now(), submitted_at timestamptz, assessed_at timestamptz,
 updated_at timestamptz not null default now(),
 constraint uq_assessment_attempt_request unique(user_id,client_key),
 constraint ck_assessment_audio check ((skill='WRITING' and audio_object_key is null) or (skill='SPEAKING' and audio_object_key is not null))
);
create index idx_assessment_attempt_history on assessment_attempts(user_id,task_id,created_at desc);
create index idx_assessment_attempt_review on assessment_attempts(status,submitted_at);
create trigger trg_assessment_attempt_updated before update on assessment_attempts for each row execute function set_updated_at();

create table assessment_reviews (
 id uuid primary key, attempt_id uuid not null references assessment_attempts(id),
 reviewed_by_user_id uuid not null references users(id), previous_report jsonb, report jsonb not null,
 note text not null, created_at timestamptz not null default now()
);
create index idx_assessment_reviews_attempt on assessment_reviews(attempt_id,created_at desc);

