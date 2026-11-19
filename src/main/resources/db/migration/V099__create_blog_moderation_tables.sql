-- A reader's report of a post or a comment. Resolved once, by a moderator acting on the target. Deleted with the
-- reporter's account.
create table blog_reports (
    id uuid primary key,
    reporter_id uuid not null references users (id) on delete cascade,
    target_type varchar(20) not null check (target_type in ('POST', 'COMMENT')),
    -- A post or a comment id; no foreign key because it can point at either table.
    target_id uuid not null,
    reason varchar(30) not null
        check (reason in ('SPAM', 'HARASSMENT', 'INAPPROPRIATE', 'MISINFORMATION', 'OTHER')),
    detail varchar(500),
    status varchar(20) not null default 'OPEN' check (status in ('OPEN', 'RESOLVED', 'DISMISSED')),
    -- Staff accounts are removed by an admin and cannot erase themselves, so these never block a learner's erasure.
    resolved_by_user_id uuid references users (id) on delete restrict,
    resolution_note varchar(500),
    resolved_at timestamptz,
    created_at timestamptz not null default now(),
    constraint ck_blog_reports_resolution check ((status = 'OPEN') = (resolved_at is null))
);
-- One OPEN report per reader and target. Closed reports do not block a new one: dismissed content can be edited
-- into something worth reporting again.
create unique index uq_blog_reports_open on blog_reports (reporter_id, target_type, target_id) where status = 'OPEN';
create index idx_blog_reports_queue on blog_reports (status, created_at);
create index idx_blog_reports_target on blog_reports (target_type, target_id, status);

-- Append-only history of what moderators did, kept even after the target is deleted (no foreign key to it).
create table blog_moderation_actions (
    id uuid primary key,
    target_type varchar(20) not null check (target_type in ('POST', 'COMMENT')),
    target_id uuid not null,
    action varchar(20) not null check (action in ('HIDE', 'RESTORE', 'REMOVE', 'DISMISS')),
    reason varchar(500) not null check (btrim(reason) <> ''),
    moderator_id uuid not null references users (id) on delete restrict,
    -- The most recent open report this action closed; null when a moderator acted without one.
    report_id uuid references blog_reports (id) on delete set null,
    created_at timestamptz not null default now()
);
create index idx_blog_moderation_actions_target on blog_moderation_actions (target_type, target_id, created_at desc);
create index idx_blog_moderation_actions_recent on blog_moderation_actions (created_at desc);
