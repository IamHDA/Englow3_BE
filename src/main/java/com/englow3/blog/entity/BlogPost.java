package com.englow3.blog.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * A knowledge-sharing post. It is visible as soon as it is posted; only its author edits or deletes it; once it is
 * hidden or removed it can be neither edited nor interacted with, though its author can still read it and delete it. A
 * moderator hides it (reversible), restores a hidden one, or removes it (permanent): {@link #hide}, {@link #restore}
 * and {@link #remove} are the only ways out of PUBLISHED and the only way back, and none of them is the author's.
 * <p>
 * {@code authorId} is null once the author's account is erased: the post stays, as by a deleted user, and nobody is its
 * author any more. {@code version} makes an author's edit fail rather than silently undo a moderator's decision made
 * while the editor was open.
 * <p>
 * The limits are also check constraints, because a row must not be able to break them by any route. Here they give the
 * caller an error with a code instead of a constraint name.
 */
@Entity
@Table(name = "blog_posts")
@Getter
public class BlogPost extends BasePersistedEntity {

    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_BODY_LENGTH = 20_000;
    static final int MAX_TAGS = 8;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlogPostStatus status;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @ElementCollection
    @CollectionTable(name = "blog_post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Getter(AccessLevel.NONE)
    private Set<BlogTag> tags = new HashSet<>();

    protected BlogPost() {
    }

    /** The title is kept stripped of the spaces around it; the body exactly as written. */
    public static BlogPost publish(UUID authorId, String title, String body, Set<BlogTag> tags, Instant now) {
        BlogPost post = new BlogPost();
        post.id = UUID.randomUUID();
        post.authorId = authorId;
        post.title = validTitle(title);
        post.body = validBody(body);
        post.tags = validTags(tags);
        post.status = BlogPostStatus.PUBLISHED;
        post.createdAt = now;
        post.updatedAt = now;
        return post;
    }

    /** Replaces the title, body and every tag. Nothing changes unless all of them are acceptable. */
    public void edit(UUID actorId, String title, String body, Set<BlogTag> tags, Instant now) {
        if (!isAuthor(actorId)) {
            throw new ForbiddenException("BLOG_POST_NOT_AUTHOR", "Only the author can edit a post");
        }
        if (status != BlogPostStatus.PUBLISHED) {
            throw new ConflictException("BLOG_POST_NOT_EDITABLE", "A hidden or removed post cannot be edited");
        }
        String newTitle = validTitle(title);
        String newBody = validBody(body);
        Set<BlogTag> newTags = validTags(tags);

        this.title = newTitle;
        this.body = newBody;
        this.tags.clear();
        this.tags.addAll(newTags);
        this.editedAt = now;
        this.updatedAt = now;
    }

    /** Moderation: takes a published post out of sight. It can be restored. */
    public void hide(Instant now) {
        if (status != BlogPostStatus.PUBLISHED) {
            throw invalidTransition("Only a published post can be hidden");
        }
        moderate(BlogPostStatus.HIDDEN, now);
    }

    /** Moderation: puts a hidden post back. A removed post stays removed. */
    public void restore(Instant now) {
        if (status != BlogPostStatus.HIDDEN) {
            throw invalidTransition("Only a hidden post can be restored");
        }
        moderate(BlogPostStatus.PUBLISHED, now);
    }

    /** Moderation: takes a published or hidden post down for good. */
    public void remove(Instant now) {
        if (status == BlogPostStatus.REMOVED) {
            throw invalidTransition("The post is already removed");
        }
        moderate(BlogPostStatus.REMOVED, now);
    }

    /** Not {@code editedAt}: that is when the author last changed the text, which a moderator did not. */
    private void moderate(BlogPostStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    private static ConflictException invalidTransition(String message) {
        return new ConflictException("BLOG_MODERATION_INVALID_TRANSITION", message);
    }

    /** Deleting is the author's, in every status: a hidden post can still be taken down by the person who wrote it. */
    public void ensureDeletableBy(UUID actorId) {
        if (!isAuthor(actorId)) {
            throw new ForbiddenException("BLOG_POST_NOT_AUTHOR", "Only the author can delete a post");
        }
    }

    /** Commenting, liking and saving: only while the post is published. */
    public void ensureOpenForInteraction() {
        if (!isOpenForInteraction()) {
            throw new ConflictException("BLOG_POST_NOT_OPEN",
                    "A hidden or removed post cannot be commented on, liked or saved");
        }
    }

    /** Reporting: only a published post. A hidden or removed one has been taken out of sight already. */
    public void ensureReportable() {
        if (status != BlogPostStatus.PUBLISHED) {
            throw new ConflictException("BLOG_POST_NOT_OPEN", "A hidden or removed post cannot be reported");
        }
    }

    public boolean isOpenForInteraction() {
        return status == BlogPostStatus.PUBLISHED;
    }

    /** What {@link #edit} would accept from this person: their own post, while it is published. */
    public boolean isEditableBy(UUID userId) {
        return isAuthor(userId) && status == BlogPostStatus.PUBLISHED;
    }

    public boolean isAuthor(UUID userId) {
        return authorId != null && authorId.equals(userId);
    }

    /** Everyone reads a published post; only its author reads it once it is hidden or removed. */
    public boolean isReadableBy(UUID viewerId) {
        return status == BlogPostStatus.PUBLISHED || isAuthor(viewerId);
    }

    /** A view: the set cannot be changed from outside, so every change to a post's tags goes through {@link #edit}. */
    public Set<BlogTag> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    private static String validTitle(String title) {
        String stripped = title == null ? "" : title.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_TITLE_LENGTH) {
            throw new BadRequestException("BLOG_TITLE_INVALID",
                    "A title needs 1 to %d characters".formatted(MAX_TITLE_LENGTH));
        }
        return stripped;
    }

    private static String validBody(String body) {
        if (body == null || body.isBlank() || body.length() > MAX_BODY_LENGTH) {
            throw new BadRequestException("BLOG_BODY_INVALID",
                    "A post needs 1 to %d characters of text".formatted(MAX_BODY_LENGTH));
        }
        return body;
    }

    private static Set<BlogTag> validTags(Set<BlogTag> tags) {
        if (tags.size() > MAX_TAGS) {
            throw new BadRequestException("BLOG_TOO_MANY_TAGS", "A post may have at most %d tags".formatted(MAX_TAGS));
        }
        return new HashSet<>(tags);
    }
}
