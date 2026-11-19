-- One conversation per pair of friends. Who is in it, and how far each has read, are participant rows.
create table chat_conversations (
    id uuid primary key,
    -- "<smaller uuid>:<larger uuid>": the only place the pair is sorted, so two friends opening a chat at the same
    -- moment end up in one conversation.
    direct_key varchar(73) not null,
    last_seq bigint not null default 0,
    last_message_at timestamptz,
    created_at timestamptz not null default now(),
    constraint uq_chat_conversations_direct_key unique (direct_key),
    constraint ck_chat_conversations_last_seq check (last_seq >= 0)
);

create table chat_participants (
    conversation_id uuid not null references chat_conversations (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    last_read_seq bigint not null default 0,
    joined_at timestamptz not null default now(),
    primary key (conversation_id, user_id)
);
create index idx_chat_participants_user on chat_participants (user_id);

create table chat_messages (
    id uuid primary key,
    conversation_id uuid not null references chat_conversations (id) on delete cascade,
    seq bigint not null,
    -- Null once the sender's account is erased: the message stays for the other participant.
    sender_id uuid references users (id) on delete set null,
    client_id varchar(64) not null,
    body text,
    image_object_key varchar(512),
    recalled_at timestamptz,
    sent_at timestamptz not null default now(),
    constraint uq_chat_messages_seq unique (conversation_id, seq),
    constraint uq_chat_messages_client unique (sender_id, client_id),
    constraint ck_chat_messages_body_length check (body is null or char_length(body) <= 4000),
    constraint ck_chat_messages_content check (
        (recalled_at is null and (body is not null or image_object_key is not null))
        or (recalled_at is not null and body is null and image_object_key is null))
);

-- Images a participant was given an upload URL for and has not yet attached to a message. Sending consumes the row;
-- the cleanup job removes rows (and objects) nobody sent within a day.
create table chat_image_uploads (
    id uuid primary key,
    conversation_id uuid not null references chat_conversations (id) on delete cascade,
    uploader_id uuid not null references users (id) on delete cascade,
    object_key varchar(512) not null,
    content_type varchar(50) not null,
    content_length bigint not null,
    created_at timestamptz not null default now(),
    constraint uq_chat_image_uploads_key unique (object_key)
);
create index idx_chat_image_uploads_created on chat_image_uploads (created_at);
