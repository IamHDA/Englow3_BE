create table section_parts (
    id uuid primary key,
    exam_section_id uuid not null references exam_sections (id) on delete restrict,
    order_no integer not null,
    title varchar(200) not null,
    instruction text,
    content text,
    audio_object_key varchar(500),
    image_object_key varchar(500),
    metadata jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_section_parts_exam_section_id on section_parts (exam_section_id);

create trigger trg_section_parts_set_updated_at
before update on section_parts
for each row execute function set_updated_at();
