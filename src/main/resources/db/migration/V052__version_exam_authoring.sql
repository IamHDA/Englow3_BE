alter table exams add column authoring_version bigint not null default 0, add column authoring_updated_at timestamptz not null default now();
