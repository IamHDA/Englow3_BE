alter table ai_model_policies
    add column input_cost_per_million numeric(14, 6) not null default 0,
    add column output_cost_per_million numeric(14, 6) not null default 0,
    add constraint chk_ai_model_policy_input_cost check (input_cost_per_million >= 0),
    add constraint chk_ai_model_policy_output_cost check (output_cost_per_million >= 0);

create index idx_ai_jobs_recent_failures on ai_jobs (completed_at desc)
    where status = 'FAILED';
create index idx_ai_jobs_requester_created on ai_jobs (requester_user_id, created_at desc);
