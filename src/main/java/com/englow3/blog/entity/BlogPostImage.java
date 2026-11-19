package com.englow3.blog.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * An image of a post. It is uploaded before the post that shows it is saved, so {@code postId} is null until that post
 * is saved; an image nobody attached within a day is swept with its object. A plain row: {@code postId} is never
 * written through this entity but only by {@code BlogPostImageRepository.attach}, which is conditional - it takes the
 * image only while nobody else has - and that is what keeps one image from ending up in two posts. {@code uploaderId}
 * is null once the uploader's account is erased; an image attached to a post stays with it.
 */
@Entity
@Table(name = "blog_post_images")
@Getter
public class BlogPostImage extends BasePersistedEntity {

    @Column(name = "post_id", insertable = false, updatable = false)
    private UUID postId;

    @Column(name = "uploader_id", updatable = false)
    private UUID uploaderId;

    @Column(name = "object_key", nullable = false, updatable = false, length = 512)
    private String objectKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogPostImage() {
    }

    public static BlogPostImage issue(UUID id, UUID uploaderId, String objectKey, String contentType, int sizeBytes,
            Instant now) {
        BlogPostImage image = new BlogPostImage();
        image.id = id;
        image.uploaderId = uploaderId;
        image.objectKey = objectKey;
        image.contentType = contentType;
        image.sizeBytes = sizeBytes;
        image.createdAt = now;
        return image;
    }
}
