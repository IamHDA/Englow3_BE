package com.englow3.blog.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A flat comment on a post. Only its author edits or deletes it, and a hidden or removed comment can no longer be
 * edited. {@code authorId} is null once the author's account is erased: the comment stays, as by a deleted user.
 * <p>
 * The 2000-character limit is also a check constraint; here it gives the caller an error with a code.
 */
@Entity
@Table(name = "blog_comments")
@Getter
public class BlogComment extends BasePersistedEntity {

    static final int MAX_BODY_LENGTH = 2000;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlogCommentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    protected BlogComment() {
    }

    /** The text is kept exactly as written. */
    public static BlogComment write(UUID postId, UUID authorId, String body, Instant now) {
        BlogComment comment = new BlogComment();
        comment.id = UUID.randomUUID();
        comment.postId = postId;
        comment.authorId = authorId;
        comment.body = validBody(body);
        comment.status = BlogCommentStatus.VISIBLE;
        comment.createdAt = now;
        return comment;
    }

    public void edit(UUID actorId, String body, Instant now) {
        if (!isAuthor(actorId)) {
            throw new ForbiddenException("BLOG_COMMENT_NOT_AUTHOR", "Only the author can edit a comment");
        }
        if (status != BlogCommentStatus.VISIBLE) {
            throw new ConflictException("BLOG_COMMENT_NOT_EDITABLE", "A hidden or removed comment cannot be edited");
        }
        this.body = validBody(body);
        this.editedAt = now;
    }

    /** Deleting is the author's, in every status. */
    public void ensureDeletableBy(UUID actorId) {
        if (!isAuthor(actorId)) {
            throw new ForbiddenException("BLOG_COMMENT_NOT_AUTHOR", "Only the author can delete a comment");
        }
    }

    /** What {@link #edit} would accept from this person: their own comment, while it is visible. */
    public boolean isEditableBy(UUID userId) {
        return isAuthor(userId) && status == BlogCommentStatus.VISIBLE;
    }

    public boolean isAuthor(UUID userId) {
        return authorId != null && authorId.equals(userId);
    }

    private static String validBody(String body) {
        if (body == null || body.isBlank() || body.length() > MAX_BODY_LENGTH) {
            throw new BadRequestException("BLOG_COMMENT_INVALID",
                    "A comment needs 1 to %d characters".formatted(MAX_BODY_LENGTH));
        }
        return body;
    }
}
