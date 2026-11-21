-- A reader's private shelf of posts to come back to. One row per person and post; the primary key is what makes saving
-- twice one save. Rows go with the post and with the account, by cascade.
create table blog_post_saves (
    post_id uuid not null references blog_posts (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);
-- The saved list: one person's saves, the latest first.
create index idx_blog_post_saves_user_created on blog_post_saves (user_id, created_at desc, post_id);
