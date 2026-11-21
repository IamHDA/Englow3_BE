package com.englow3.blog.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.blog.entity.BlogPostSave;
import com.englow3.blog.entity.BlogPostSaveId;

public interface BlogPostSaveRepository extends JpaRepository<BlogPostSave, BlogPostSaveId> {

    /** Saving twice - two tabs, a retried request - is one row: the second insert finds the key taken. */
    @Modifying
    @Query(value = """
            insert into blog_post_saves (post_id, user_id, created_at) values (:post, :user, :now)
            on conflict (post_id, user_id) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(@Param("post") UUID postId, @Param("user") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from BlogPostSave s where s.id.postId = :post and s.id.userId = :user")
    int deleteSave(@Param("post") UUID postId, @Param("user") UUID userId);
}
