package com.englow3.blog.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One person's like of one post. A plain row, read through here and never saved from here: a like is inserted by a
 * native {@code on conflict do nothing}, so liking twice - two tabs, a retried request - is one row, and the primary
 * key is what guarantees it.
 */
@Entity
@Table(name = "blog_post_likes")
@Getter
public class BlogPostLike {

    @EmbeddedId
    private BlogPostLikeId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogPostLike() {
    }
}
