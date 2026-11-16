package com.englow3.blog.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.englow3.blog.entity.BlogPost;

public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

    /** The post with its tags in the one query: nothing outside the transaction can load them later. */
    @EntityGraph(attributePaths = "tags")
    Optional<BlogPost> findWithTagsById(UUID id);
}
