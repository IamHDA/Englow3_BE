-- "Liked this week" in the blog's recommendations reads the likes given since a moment, across every post. The primary
-- key leads with post_id and the other index with user_id, so without this the query reads every like there is.
create index idx_blog_post_likes_created on blog_post_likes (created_at, post_id);
