package com.englow3.blog.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.blog.entity.BlogPost;

import jakarta.persistence.LockModeType;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    /** The post with its tags in the one query: nothing outside the transaction can load them later. */
    @EntityGraph(attributePaths = "tags")
    Optional<BlogPost> findWithTagsById(UUID id);

    /**
     * The post with its row locked until the transaction ends, read fresh from the database: a moderator's decision is
     * made on the state the post has now, and an author's save that is in flight either finishes first or finds the
     * version changed. Also how an author's delete reads the post, so that it waits for an action in progress instead
     * of failing on the version it had read.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BlogPost p where p.id = :id")
    Optional<BlogPost> findByIdForUpdate(@Param("id") UUID id);

    /** Whether the post exists and this person wrote it: nothing is loaded, so nothing is held. */
    boolean existsByIdAndAuthorId(UUID id, UUID authorId);
}
