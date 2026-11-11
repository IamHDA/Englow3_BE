alter table score_conversions
    add column source_reference varchar(1000),
    add column created_by_user_id uuid references users(id) on delete set null;
update score_conversions set source_reference = 'IELTS guide and internal low-score extension; see V081__ielts_score_conversions.sql'
    where certificate_type = 'IELTS';
