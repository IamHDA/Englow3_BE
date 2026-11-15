-- Friend requests and friendships between learners. One row per pair, whichever side asked:
-- declining, cancelling and unfriending delete the row, so no request history is kept.
create table friendships (
    id uuid primary key,
    requester_id uuid not null references users (id) on delete cascade,
    addressee_id uuid not null references users (id) on delete cascade,
    status varchar(20) not null check (status in ('PENDING', 'ACCEPTED')),
    requested_at timestamptz not null default now(),
    accepted_at timestamptz,
    constraint ck_friendships_not_self check (requester_id <> addressee_id),
    constraint ck_friendships_accepted_at check ((status = 'ACCEPTED') = (accepted_at is not null))
);

-- A asking B and B asking A are the same pair: the order of the two ids must not matter.
create unique index uq_friendships_pair
    on friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));
create index idx_friendships_requester_status on friendships (requester_id, status);
create index idx_friendships_addressee_status on friendships (addressee_id, status);

create table user_blocks (
    blocker_id uuid not null references users (id) on delete cascade,
    blocked_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (blocker_id, blocked_id),
    constraint ck_user_blocks_not_self check (blocker_id <> blocked_id)
);
create index idx_user_blocks_blocked on user_blocks (blocked_id);
