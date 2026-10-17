create table learning_purposes (
    id integer generated always as identity primary key,
    purpose_code varchar(30) not null unique,
    display_name varchar(100) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create trigger trg_learning_purposes_set_updated_at
before update on learning_purposes
for each row execute function set_updated_at();
