package com.englow3.blog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;

class BlogCommentTest {

    private static final Instant WRITTEN = Instant.parse("2026-11-16T08:00:00Z");
    private static final Instant EDITED = Instant.parse("2026-11-16T08:10:00Z");

    private final UUID postId = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private BlogComment comment() {
        return BlogComment.write(postId, author, "Thanks, this helped", WRITTEN);
    }

    /** Hiding and removing belong to moderation, which does not exist yet; the tests put a comment there by hand. */
    private static BlogComment inStatus(BlogComment comment, BlogCommentStatus status) {
        ReflectionTestUtils.setField(comment, "status", status);
        return comment;
    }

    @Nested
    class Writing {

        @Test
        void aNewCommentIsVisibleAndNeverEdited() {
            BlogComment comment = comment();

            assertThat(comment.getId()).isNotNull();
            assertThat(comment.getPostId()).isEqualTo(postId);
            assertThat(comment.getAuthorId()).isEqualTo(author);
            assertThat(comment.getBody()).isEqualTo("Thanks, this helped");
            assertThat(comment.getStatus()).isEqualTo(BlogCommentStatus.VISIBLE);
            assertThat(comment.getCreatedAt()).isEqualTo(WRITTEN);
            assertThat(comment.getEditedAt()).isNull();
        }

        @Test
        void theTextIsKeptExactlyAsWritten() {
            assertThat(BlogComment.write(postId, author, "  two\nlines  ", WRITTEN).getBody())
                    .isEqualTo("  two\nlines  ");
        }

        @Test
        void aBlankOrOver2000CharacterCommentIsRefused() {
            for (String body : new String[] { "", "  \n ", "x".repeat(2001) }) {
                assertThatThrownBy(() -> BlogComment.write(postId, author, body, WRITTEN)).isInstanceOfSatisfying(
                        BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_INVALID"));
            }
        }

        @Test
        void aComment2000CharactersLongIsAccepted() {
            assertThat(BlogComment.write(postId, author, "x".repeat(2000), WRITTEN).getBody()).hasSize(2000);
        }
    }

    @Nested
    class Editing {

        @Test
        void theAuthorEditsAndTheEditIsMarked() {
            BlogComment comment = comment();

            comment.edit(author, "Thanks, this helped a lot", EDITED);

            assertThat(comment.getBody()).isEqualTo("Thanks, this helped a lot");
            assertThat(comment.getEditedAt()).isEqualTo(EDITED);
            assertThat(comment.getCreatedAt()).isEqualTo(WRITTEN);
        }

        @Test
        void someoneElseCannotEdit() {
            BlogComment comment = comment();

            assertThatThrownBy(() -> comment.edit(other, "Mine now", EDITED)).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_AUTHOR"));
            assertThat(comment.getBody()).isEqualTo("Thanks, this helped");
        }

        @Test
        void aHiddenOrRemovedCommentCannotBeEdited() {
            for (BlogCommentStatus status : new BlogCommentStatus[] { BlogCommentStatus.HIDDEN,
                    BlogCommentStatus.REMOVED }) {
                BlogComment comment = inStatus(comment(), status);

                assertThatThrownBy(() -> comment.edit(author, "Trying again", EDITED)).isInstanceOfSatisfying(
                        ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_EDITABLE"));
            }
        }

        @Test
        void anEditHoldsToTheSameLimitAndChangesNothingWhenRefused() {
            BlogComment comment = comment();

            assertThatThrownBy(() -> comment.edit(author, " ", EDITED)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_INVALID"));

            assertThat(comment.getBody()).isEqualTo("Thanks, this helped");
            assertThat(comment.getEditedAt()).isNull();
        }
    }

    @Nested
    class WhoIsTheAuthor {

        @Test
        void theAuthorDeletesInEveryStatusAndNobodyElseDoes() {
            for (BlogCommentStatus status : BlogCommentStatus.values()) {
                BlogComment comment = inStatus(comment(), status);

                comment.ensureDeletableBy(author);
                assertThatThrownBy(() -> comment.ensureDeletableBy(other)).isInstanceOfSatisfying(
                        ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_AUTHOR"));
            }
        }

        @Test
        void aCommentWhoseAuthorIsErasedHasNoAuthor() {
            BlogComment orphan = BlogComment.write(postId, null, "left behind", WRITTEN);

            assertThat(orphan.isAuthor(author)).isFalse();
            assertThat(orphan.isAuthor(null)).isFalse();
            assertThatThrownBy(() -> orphan.ensureDeletableBy(author)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void aCommentIsEditableOnlyByItsAuthorAndOnlyWhileVisible() {
            assertThat(comment().isEditableBy(author)).isTrue();
            assertThat(comment().isEditableBy(other)).isFalse();
            assertThat(inStatus(comment(), BlogCommentStatus.HIDDEN).isEditableBy(author)).isFalse();
        }
    }
}
