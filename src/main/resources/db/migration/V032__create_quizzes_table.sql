create table quizzes (
    id uuid primary key,
    slug varchar(120) not null unique,
    title varchar(200) not null,
    description text not null default '',
    category varchar(60) not null,
    -- CEFR band the quiz is aimed at. Null on a quiz that deliberately mixes levels.
    target_level varchar(2),
    time_limit_seconds integer not null,
    -- Percentage, not a raw score: a quiz can gain a question without its pass mark becoming wrong.
    passing_score_percent smallint not null,
    status varchar(20) not null,
    created_by_user_id uuid not null references users (id) on delete restrict,
    published_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_quizzes_status on quizzes (status);

create trigger trg_quizzes_set_updated_at
before update on quizzes
for each row execute function set_updated_at();
