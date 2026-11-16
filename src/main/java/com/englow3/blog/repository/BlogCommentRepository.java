package com.englow3.blog.repository;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.blog.entity.BlogComment;
import com.englow3.blog.entity.BlogCommentStatus;

public interface BlogCommentRepository extends JpaRepository<BlogComment, UUID> {

    /**
     * The comments the viewer may see: everyone's visible ones, and the viewer's own in any status, which is how an
     * author finds a comment that was hidden or removed. Nobody else sees those. A comment whose author is erased
     * (null) is still shown while it is visible. The caller sorts by {@code createdAt, id} and must pass at least one
     * id in {@code hiddenAuthors}: {@code not in} an empty list is not valid SQL on every path Hibernate takes.
     */
    @Query("""
            select c from BlogComment c
            where c.postId = :post
              and (c.status = com.englow3.blog.entity.BlogCommentStatus.VISIBLE or c.authorId = :me)
              and (c.authorId is null or c.authorId not in :hidden)
            """)
    Page<BlogComment> findVisible(@Param("post") UUID postId, @Param("me") UUID me,
            @Param("hidden") Collection<UUID> hiddenAuthors, Pageable page);

    /** The number shown on a post: visible comments only, whoever is asking. */
    long countByPostIdAndStatus(UUID postId, BlogCommentStatus status);
}
