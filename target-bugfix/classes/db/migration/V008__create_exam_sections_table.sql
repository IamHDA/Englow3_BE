create table exam_sections (
    id uuid primary key,
    exam_id uuid not null references exams (id) on delete restrict,
    section_type varchar(20) not null,
    order_no integer not null,
    max_raw_score numeric(8, 2) not null,
    is_scored_by_criteria boolean not null default false,
    time_limit_seconds integer,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_exam_sections_exam_id on exam_sections (exam_id);

create trigger trg_exam_sections_set_updated_at
before update on exam_sections
for each row execute function set_updated_at();
