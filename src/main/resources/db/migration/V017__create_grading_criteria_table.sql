create table grading_criteria (
    id integer generated always as identity primary key,
    certificate_type varchar(20) not null,
    section_type varchar(20) not null,
    criterion_code varchar(40) not null,
    display_name varchar(100) not null,
    max_score numeric(4, 1) not null,
    weight numeric(4, 2) not null default 1.00,
    order_no integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (certificate_type, section_type, criterion_code)
);

create trigger trg_grading_criteria_set_updated_at
before update on grading_criteria
for each row execute function set_updated_at();
