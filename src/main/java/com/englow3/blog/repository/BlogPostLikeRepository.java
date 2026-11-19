package com.englow3.blog.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.blog.entity.BlogPostLike;
import com.englow3.blog.entity.BlogPostLikeId;

public interface BlogPostLikeRepository extends JpaRepository<BlogPostLike, BlogPostLikeId> {

    /**
     * Liking twice - two tabs, a retried request - is one row: the second insert finds the key taken and does nothing.
     */
    @Modifying
    @Query(value = """
            insert into blog_post_likes (post_id, user_id, created_at) values (:post, :user, :now)
            on conflict (post_id, user_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("post") UUID postId, @Param("user") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from BlogPostLike l where l.id.postId = :post and l.id.userId = :user")
    int deleteLike(@Param("post") UUID postId, @Param("user") UUID userId);

    long countByIdPostId(UUID postId);

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);
}
