-- Keep only non-content delivery IDs so an old browser outbox cannot resurrect a deleted conversation.
create table tutor_deleted_requests (
    user_id uuid not null references users(id) on delete cascade,
    client_message_id uuid not null,
    deleted_at timestamptz not null default now(),
    primary key(user_id, client_message_id)
);
create index idx_tutor_deleted_requests_cleanup on tutor_deleted_requests(deleted_at);
