package com.englow3.blog.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

@Embeddable
@Getter
public class BlogPostLikeId implements Serializable {

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    protected BlogPostLikeId() {
    }

    public BlogPostLikeId(UUID postId, UUID userId) {
        this.postId = postId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlogPostLikeId that && Objects.equals(postId, that.postId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, userId);
    }
}
