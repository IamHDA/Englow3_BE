create table flashcard_sets (
    id uuid primary key,
    slug varchar(120) not null unique,
    name varchar(200) not null,
    description text not null default '',
    topic varchar(60) not null,
    -- Null on a set that deliberately mixes levels, which is most revision sets.
    target_level varchar(2),
    status varchar(20) not null,
    created_by_user_id uuid not null references users (id) on delete restrict,
    published_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_flashcard_sets_status on flashcard_sets (status);

create trigger trg_flashcard_sets_set_updated_at
before update on flashcard_sets
for each row execute function set_updated_at();
