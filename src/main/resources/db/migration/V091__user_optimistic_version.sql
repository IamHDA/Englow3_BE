-- Profile/onboarding writes must not overwrite a concurrent administrator role change.
alter table users add column version bigint not null default 0;
