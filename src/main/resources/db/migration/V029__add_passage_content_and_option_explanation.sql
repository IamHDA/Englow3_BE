alter table question_sets
    add column content text,
    add column metadata jsonb;

alter table question_options
    add column explanation text;
