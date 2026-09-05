create table question_sets (
    id uuid primary key,
    section_part_id uuid not null references section_parts (id) on delete restrict,
    title varchar(200),
    instruction text,
    order_no integer not null,
    audio_object_key varchar(500),
    image_object_key varchar(500),
    -- the passage a group of questions reads from, and free-form delivery data (TOEIC Part 6/7)
    content text,
    metadata jsonb,
    is_single_use boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_question_sets_section_part_id on question_sets (section_part_id);

create trigger trg_question_sets_set_updated_at
before update on question_sets
for each row execute function set_updated_at();
