create table tutor_policy (
    scope varchar(60) primary key,
    accepting boolean not null default true,
    allowed_modes jsonb not null default '["GENERAL","CORRECTION","CONVERSATION","PRACTICE"]',
    version bigint not null default 0 check(version>=0),
    updated_at timestamptz not null default now(),
    check(jsonb_typeof(allowed_modes)='array')
);
