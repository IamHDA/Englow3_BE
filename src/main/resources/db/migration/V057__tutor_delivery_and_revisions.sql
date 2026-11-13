alter table tutor_messages add column revision integer not null default 0 check (revision >= 0);

create table tutor_requests (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    client_message_id uuid not null,
    fingerprint varchar(64) not null,
    conversation_id uuid not null references tutor_conversations(id) on delete cascade,
    question_id uuid not null references tutor_messages(id) on delete cascade,
    answer_id uuid not null references tutor_messages(id) on delete cascade,
    created_at timestamptz not null default now(),
    unique(user_id, client_message_id)
);

create table tutor_reply_versions (
    id uuid primary key,
    message_id uuid not null references tutor_messages(id) on delete cascade,
    revision integer not null,
    status varchar(20) not null,
    content text,
    model varchar(200),
    error_code varchar(100),
    input_tokens integer,
    output_tokens integer,
    answered_at timestamptz,
    created_at timestamptz not null default now(),
    unique(message_id, revision)
);

create index idx_tutor_messages_pending on tutor_messages(tutor_conversation_id) where status = 'PENDING';
create index idx_tutor_requests_created on tutor_requests(user_id, created_at desc);
