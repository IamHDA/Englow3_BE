-- AI tutor: a conversation a learner keeps, and the turns in it.
--
-- Messages are a table of their own rather than a jsonb array on the
-- conversation. A reply arrives asynchronously and is updated in place when it
-- does, one row at a time; rewriting a growing array on every turn would make
-- each message cost more than the one before it, and two turns landing together
-- would lose one.

create table tutor_conversations (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,

    -- Taken from the first thing the learner says, so the list reads as what
    -- they asked rather than "Conversation 4".
    title varchar(200) not null,

    -- What the tutor is being asked to do. Free-form rather than an enum: the
    -- set of things a tutor is useful for is not something to migrate for.
    topic varchar(60),

    -- Bumped on every turn so the list can sort by activity without reading the
    -- messages. Not derivable cheaply once a conversation is long.
    last_message_at timestamptz not null default now(),
    message_count integer not null default 0,

    archived_at timestamptz,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_tutor_conversations_user_id_last_message_at
    on tutor_conversations (user_id, last_message_at desc);

create trigger trg_tutor_conversations_set_updated_at
before update on tutor_conversations
for each row execute function set_updated_at();

create table tutor_messages (
    id uuid primary key,
    tutor_conversation_id uuid not null
        references tutor_conversations (id) on delete cascade,

    -- Position in the conversation. Explicit rather than ordering by created_at:
    -- a learner's message and the reply it triggers can land in the same
    -- millisecond, and then the transcript reads backwards.
    order_no integer not null,

    -- USER or ASSISTANT. The learner's turn is stored the moment they send it;
    -- the tutor's is stored empty and filled when the provider answers.
    role varchar(20) not null,

    -- PENDING, READY or FAILED. A learner's message is READY on arrival - there
    -- is nothing to wait for. Only an assistant turn is ever PENDING.
    status varchar(20) not null,

    -- Null while a reply is still PENDING. Deliberately not defaulted to an
    -- empty string: "the tutor has not answered yet" and "the tutor answered
    -- with nothing" are different, and a screen has to be able to tell them apart.
    content text,

    -- Why no reply came, when none did. Shown to the learner as an explanation
    -- rather than a blank bubble.
    error_code varchar(100),

    -- What produced this turn, kept per message rather than per conversation:
    -- the model can change mid-conversation, and an answer should say which one
    -- gave it.
    model varchar(200),
    prompt_version varchar(60),
    input_tokens integer,
    output_tokens integer,

    -- Set when a learner reports the answer as wrong or inappropriate. The note
    -- is theirs, in their words; nothing parses it.
    reported_at timestamptz,
    report_note text,

    created_at timestamptz not null default now(),
    answered_at timestamptz,
    updated_at timestamptz not null default now(),

    unique (tutor_conversation_id, order_no)
);

create index idx_tutor_messages_conversation_id_order_no
    on tutor_messages (tutor_conversation_id, order_no);

-- Reported answers, newest first, for whoever reviews them. Partial so the
-- index stays the size of the problem rather than the size of the table.
create index idx_tutor_messages_reported_at
    on tutor_messages (reported_at desc)
    where reported_at is not null;

create trigger trg_tutor_messages_set_updated_at
before update on tutor_messages
for each row execute function set_updated_at();
