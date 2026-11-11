alter table learner_profiles
    add column level_measured_at timestamptz,
    add column level_measurement_final boolean not null default false,
    add column version bigint not null default 0;
