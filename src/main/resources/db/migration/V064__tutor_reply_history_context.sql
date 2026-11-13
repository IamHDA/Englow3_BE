alter table tutor_messages add column restart_count integer not null default 0 check(restart_count between 0 and 3);
update tutor_messages set restart_count=least(revision,3);
alter table tutor_reply_versions add column sources_json jsonb not null default '[]';
alter table tutor_reply_versions add column correction text;
