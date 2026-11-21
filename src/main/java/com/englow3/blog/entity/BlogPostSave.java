package com.englow3.blog.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One person's save of one post. A plain row, like a like: inserted by a native {@code on conflict do nothing}, so
 * saving twice - two tabs, a retried request - is one row, and the primary key is what guarantees it. There is no rule
 * here to protect; who may save a post is decided where the save is made.
 */
@Entity
@Table(name = "blog_post_saves")
@Getter
public class BlogPostSave {

    @EmbeddedId
    private BlogPostSaveId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogPostSave() {
    }
}
