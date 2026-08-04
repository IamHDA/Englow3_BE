create table user_learning_purposes (
    user_id uuid not null references users (id) on delete restrict,
    learning_purpose_id integer not null references learning_purposes (id) on delete restrict,
    created_at timestamptz not null default now(),
    primary key (user_id, learning_purpose_id)
);

create index idx_user_learning_purposes_learning_purpose_id on user_learning_purposes (learning_purpose_id);
