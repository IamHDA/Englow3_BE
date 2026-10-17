create table score_conversions (
    id integer generated always as identity primary key,
    certificate_type varchar(20) not null,
    certificate_variant varchar(30),
    section_type varchar(20),
    raw_score_from integer not null,
    raw_score_to integer not null,
    converted_score numeric(5, 1) not null,
    cefr_level varchar(2),
    effective_from date not null,
    created_at timestamptz not null default now(),
    unique (certificate_type, certificate_variant, section_type, raw_score_from, effective_from)
);

create index idx_score_conversions_certificate_type_section_type_raw_from
    on score_conversions (certificate_type, section_type, raw_score_from);
