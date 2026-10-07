alter table flashcard_sets add column version bigint not null default 0, add column authoring_updated_at timestamptz not null default now();
alter table dictation_lessons add column version bigint not null default 0, add column authoring_updated_at timestamptz not null default now();
alter table quizzes add column version bigint not null default 0, add column authoring_updated_at timestamptz not null default now();
alter table speaking_prompts add column version bigint not null default 0, add column authoring_updated_at timestamptz not null default now();
