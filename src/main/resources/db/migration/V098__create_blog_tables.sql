-- Knowledge-sharing posts. Visible as soon as they are posted; moderation (HIDDEN, REMOVED) follows reports.
create table blog_posts (
    id uuid primary key,
    -- Null once the author's account is erased: the post stays, shown as by a deleted user.
    author_id uuid references users (id) on delete set null,
    title varchar(200) not null,
    -- Markdown. Images are referenced as blog-image:<id>, never by URL, so the bucket can stay private.
    body text not null,
    status varchar(20) not null default 'PUBLISHED' check (status in ('PUBLISHED', 'HIDDEN', 'REMOVED')),
    version bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    edited_at timestamptz,
    constraint ck_blog_posts_title check (btrim(title) <> ''),
    constraint ck_blog_posts_body check (btrim(body) <> '' and char_length(body) <= 20000)
);
create index idx_blog_posts_status_created on blog_posts (status, created_at desc, id);
create index idx_blog_posts_author_created on blog_posts (author_id, created_at desc);

create table blog_post_tags (
    post_id uuid not null references blog_posts (id) on delete cascade,
    kind varchar(20) not null check (kind in ('SKILL', 'LEVEL', 'CERTIFICATE')),
    tag_value varchar(30) not null,
    primary key (post_id, kind, tag_value)
);
create index idx_blog_post_tags_lookup on blog_post_tags (kind, tag_value, post_id);

-- An image is uploaded before its post is saved, so post_id stays null until the post that references it is saved.
-- Deleting a post deletes its rows here by cascade but not their objects in the bucket: whoever deletes a post must read
-- the object keys first and remove the objects after the commit.
create table blog_post_images (
    id uuid primary key,
    post_id uuid references blog_posts (id) on delete cascade,
    uploader_id uuid references users (id) on delete set null,
    object_key varchar(512) not null,
    content_type varchar(50) not null,
    size_bytes integer not null,
    created_at timestamptz not null default now(),
    constraint uq_blog_post_images_key unique (object_key)
);
create index idx_blog_post_images_post on blog_post_images (post_id);
create index idx_blog_post_images_unattached on blog_post_images (created_at) where post_id is null;

create table blog_comments (
    id uuid primary key,
    post_id uuid not null references blog_posts (id) on delete cascade,
    -- Null once the author's account is erased: the comment stays, shown as by a deleted user.
    author_id uuid references users (id) on delete set null,
    body text not null,
    status varchar(20) not null default 'VISIBLE' check (status in ('VISIBLE', 'HIDDEN', 'REMOVED')),
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    constraint ck_blog_comments_body check (btrim(body) <> '' and char_length(body) <= 2000)
);
create index idx_blog_comments_post_created on blog_comments (post_id, created_at, id);

create table blog_post_likes (
    post_id uuid not null references blog_posts (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);
create index idx_blog_post_likes_user on blog_post_likes (user_id);
