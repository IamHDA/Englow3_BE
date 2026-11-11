-- Compare persisted assessment versions, not wall-clock timestamps, when recovering missed callbacks.
alter table full_test_sittings add column assessment_version_sum bigint not null default -1;
