create table user_role_changes (
    id uuid primary key,
    user_id uuid references users(id) on delete set null,
    actor_id uuid references users(id) on delete set null,
    previous_role varchar(10) not null check(previous_role in ('LEARNER','STAFF','ADMIN')),
    new_role varchar(10) not null check(new_role in ('LEARNER','STAFF','ADMIN')),
    reason varchar(2000) not null,
    created_at timestamptz not null
);
create index idx_user_role_changes_user on user_role_changes(user_id, created_at desc);
