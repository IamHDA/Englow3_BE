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

    /**
     * A comment in a given status without going through moderation, for the tests of what a hidden or removed comment
     * allows. The moves between statuses have their own tests, under {@code Moderating}.
     */
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

    @Nested
    class Moderating {

        private void refusedAsAnInvalidTransition(Runnable move) {
            assertThatThrownBy(move::run).isInstanceOfSatisfying(ConflictException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_INVALID_TRANSITION"));
        }

        @Test
        void aVisibleCommentCanBeHiddenAndRestored() {
            BlogComment comment = comment();

            comment.hide();
            assertThat(comment.getStatus()).isEqualTo(BlogCommentStatus.HIDDEN);

            comment.restore();
            assertThat(comment.getStatus()).isEqualTo(BlogCommentStatus.VISIBLE);
        }

        @Test
        void aVisibleOrHiddenCommentCanBeRemoved() {
            BlogComment visible = comment();
            BlogComment hidden = inStatus(comment(), BlogCommentStatus.HIDDEN);

            visible.remove();
            hidden.remove();

            assertThat(visible.getStatus()).isEqualTo(BlogCommentStatus.REMOVED);
            assertThat(hidden.getStatus()).isEqualTo(BlogCommentStatus.REMOVED);
        }

        @Test
        void moderationLeavesTheTextAndWhenItWasEditedAlone() {
            BlogComment comment = comment();
            comment.edit(author, "Edited", EDITED);

            comment.hide();

            assertThat(comment.getBody()).isEqualTo("Edited");
            assertThat(comment.getEditedAt()).isEqualTo(EDITED);
        }

        @Test
        void aCommentThatIsNotVisibleCannotBeHidden() {
            refusedAsAnInvalidTransition(() -> inStatus(comment(), BlogCommentStatus.HIDDEN).hide());
            refusedAsAnInvalidTransition(() -> inStatus(comment(), BlogCommentStatus.REMOVED).hide());
        }

        @Test
        void onlyAHiddenCommentCanBeRestored() {
            refusedAsAnInvalidTransition(() -> comment().restore());
            refusedAsAnInvalidTransition(() -> inStatus(comment(), BlogCommentStatus.REMOVED).restore());
        }

        @Test
        void aRemovedCommentCannotBeRemovedAgain() {
            refusedAsAnInvalidTransition(() -> inStatus(comment(), BlogCommentStatus.REMOVED).remove());
        }

        @Test
        void aRefusedMoveChangesNothing() {
            BlogComment removed = inStatus(comment(), BlogCommentStatus.REMOVED);

            refusedAsAnInvalidTransition(removed::restore);

            assertThat(removed.getStatus()).isEqualTo(BlogCommentStatus.REMOVED);
        }
    }
}
