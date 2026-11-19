-- Short-lived, single-use tickets that let a browser open the chat socket without ever holding its access token.
-- Only the SHA-256 of a ticket is stored; a ticket is redeemed by one conditional update, so any instance can take
-- the handshake and two sockets can never share one ticket.
create table chat_socket_tickets (
    token_hash varchar(64) primary key,
    user_id uuid not null references users (id) on delete cascade,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null default now()
);
create index idx_chat_socket_tickets_expires on chat_socket_tickets (expires_at);
