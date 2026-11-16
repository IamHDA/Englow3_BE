package com.englow3.blog.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;

class BlogPostTest {

    private static final Instant POSTED = Instant.parse("2026-11-16T08:00:00Z");
    private static final Instant EDITED = Instant.parse("2026-11-16T09:30:00Z");

    private final UUID author = UUID.randomUUID();
    private final UUID reader = UUID.randomUUID();

    private BlogPost post() {
        return BlogPost.publish(author, "Ten tips for IELTS writing", "Body in **Markdown**",
                Set.of(BlogTag.of(BlogTagKind.SKILL, "WRITING")), POSTED);
    }

    /**
     * Hiding and removing belong to moderation, which does not exist yet, so nothing in the class can put a post in
     * either state. The tests do it by hand; when the moderation methods arrive they replace this.
     */
    private static BlogPost inStatus(BlogPost post, BlogPostStatus status) {
        ReflectionTestUtils.setField(post, "status", status);
        return post;
    }

    private static Set<BlogTag> tags(int howMany) {
        Set<BlogTag> tags = new HashSet<>();
        for (BlogSkill skill : BlogSkill.values()) {
            tags.add(BlogTag.of(BlogTagKind.SKILL, skill.name()));
        }
        for (BlogLevel level : BlogLevel.values()) {
            tags.add(BlogTag.of(BlogTagKind.LEVEL, level.name()));
        }
        return Set.copyOf(tags.stream().limit(howMany).toList());
    }

    @Nested
    class Publishing {

        @Test
        void aNewPostIsPublishedAtOnceAndNeverEdited() {
            BlogPost post = post();

            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.PUBLISHED);
            assertThat(post.getId()).isNotNull();
            assertThat(post.getAuthorId()).isEqualTo(author);
            assertThat(post.getTitle()).isEqualTo("Ten tips for IELTS writing");
            assertThat(post.getBody()).isEqualTo("Body in **Markdown**");
            assertThat(post.getTags()).containsExactly(BlogTag.of(BlogTagKind.SKILL, "WRITING"));
            assertThat(post.getCreatedAt()).isEqualTo(POSTED);
            assertThat(post.getUpdatedAt()).isEqualTo(POSTED);
            assertThat(post.getEditedAt()).isNull();
        }

        @Test
        void theTitleIsStrippedAndTheBodyIsKeptExactlyAsWritten() {
            BlogPost post = BlogPost.publish(author, "  Spaced title \n", "\n  indented first line\n\n", Set.of(),
                    POSTED);

            assertThat(post.getTitle()).isEqualTo("Spaced title");
            assertThat(post.getBody()).isEqualTo("\n  indented first line\n\n");
        }

        @Test
        void aTitleOfNothingButSpacesOrOver200CharactersIsRefused() {
            for (String title : new String[] { "", "   \t ", "x".repeat(201), " " + "x".repeat(201) + " " }) {
                assertThatThrownBy(() -> BlogPost.publish(author, title, "body", Set.of(), POSTED))
                        .isInstanceOfSatisfying(BadRequestException.class,
                                e -> assertThat(e.getCode()).isEqualTo("BLOG_TITLE_INVALID"));
            }
        }

        @Test
        void aTitleOf200CharactersAfterStrippingIsAccepted() {
            BlogPost post = BlogPost.publish(author, " " + "x".repeat(200) + " ", "body", Set.of(), POSTED);

            assertThat(post.getTitle()).hasSize(200);
        }

        @Test
        void aBodyOfNothingButWhitespaceOrOver20000CharactersIsRefused() {
            for (String body : new String[] { "", " \n\t ", "x".repeat(20_001) }) {
                assertThatThrownBy(() -> BlogPost.publish(author, "Title", body, Set.of(), POSTED))
                        .isInstanceOfSatisfying(BadRequestException.class,
                                e -> assertThat(e.getCode()).isEqualTo("BLOG_BODY_INVALID"));
            }
        }

        @Test
        void aBodyOf20000CharactersIsAccepted() {
            assertThat(BlogPost.publish(author, "Title", "x".repeat(20_000), Set.of(), POSTED).getBody())
                    .hasSize(20_000);
        }

        @Test
        void nineTagsAreTooManyAndEightAreNot() {
            assertThat(BlogPost.publish(author, "Title", "body", tags(8), POSTED).getTags()).hasSize(8);

            assertThatThrownBy(() -> BlogPost.publish(author, "Title", "body", tags(9), POSTED)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_TOO_MANY_TAGS"));
        }

        @Test
        void thePostKeepsItsOwnCopyOfTheTags() {
            Set<BlogTag> given = new HashSet<>(Set.of(BlogTag.of(BlogTagKind.LEVEL, "B2")));

            BlogPost post = BlogPost.publish(author, "Title", "body", given, POSTED);
            given.clear();

            assertThat(post.getTags()).containsExactly(BlogTag.of(BlogTagKind.LEVEL, "B2"));
        }

        @Test
        void theTagsCannotBeChangedFromOutside() {
            BlogPost post = post();

            assertThatThrownBy(() -> post.getTags().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class Editing {

        @Test
        void theAuthorReplacesTitleBodyAndEveryTag() {
            BlogPost post = post();

            post.edit(author, "  A better title ", "A new body", Set.of(BlogTag.of(BlogTagKind.LEVEL, "B2")), EDITED);

            assertThat(post.getTitle()).isEqualTo("A better title");
            assertThat(post.getBody()).isEqualTo("A new body");
            assertThat(post.getTags()).containsExactly(BlogTag.of(BlogTagKind.LEVEL, "B2"));
            assertThat(post.getEditedAt()).isEqualTo(EDITED);
            assertThat(post.getUpdatedAt()).isEqualTo(EDITED);
            assertThat(post.getCreatedAt()).isEqualTo(POSTED);
            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.PUBLISHED);
        }

        @Test
        void someoneElseCannotEdit() {
            BlogPost post = post();

            assertThatThrownBy(() -> post.edit(reader, "Hijacked", "body", Set.of(), EDITED)).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_AUTHOR"));
            assertThat(post.getTitle()).isEqualTo("Ten tips for IELTS writing");
            assertThat(post.getEditedAt()).isNull();
        }

        @Test
        void aHiddenOrRemovedPostCannotBeEditedEvenByItsAuthor() {
            for (BlogPostStatus status : new BlogPostStatus[] { BlogPostStatus.HIDDEN, BlogPostStatus.REMOVED }) {
                BlogPost post = inStatus(post(), status);

                assertThatThrownBy(() -> post.edit(author, "New", "body", Set.of(), EDITED)).isInstanceOfSatisfying(
                        ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_EDITABLE"));
            }
        }

        @Test
        void aRefusedEditChangesNothing() {
            BlogPost post = post();

            assertThatThrownBy(() -> post.edit(author, "New title", "", Set.of(), EDITED)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_BODY_INVALID"));
            assertThatThrownBy(() -> post.edit(author, "New title", "New body", tags(9), EDITED))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BLOG_TOO_MANY_TAGS"));

            assertThat(post.getTitle()).isEqualTo("Ten tips for IELTS writing");
            assertThat(post.getBody()).isEqualTo("Body in **Markdown**");
            assertThat(post.getTags()).containsExactly(BlogTag.of(BlogTagKind.SKILL, "WRITING"));
            assertThat(post.getEditedAt()).isNull();
        }

        @Test
        void theSameLimitsApplyToAnEditAsToANewPost() {
            BlogPost post = post();

            assertThatThrownBy(() -> post.edit(author, " ", "body", Set.of(), EDITED)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_TITLE_INVALID"));
        }
    }

    @Nested
    class WhoIsTheAuthor {

        @Test
        void aPostWhoseAuthorIsErasedHasNoAuthorAndNobodyEditsOrDeletesIt() {
            BlogPost orphan = BlogPost.publish(null, "Title", "body", Set.of(), POSTED);

            assertThat(orphan.isAuthor(null)).isFalse();
            assertThat(orphan.isAuthor(author)).isFalse();
            assertThatThrownBy(() -> orphan.edit(author, "T", "b", Set.of(), EDITED)).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_AUTHOR"));
            assertThatThrownBy(() -> orphan.ensureDeletableBy(author)).isInstanceOf(ForbiddenException.class);
        }

        @Test
        void anErasedAuthorsPostIsStillReadableByEveryone() {
            BlogPost orphan = BlogPost.publish(null, "Title", "body", Set.of(), POSTED);

            assertThat(orphan.isReadableBy(reader)).isTrue();
        }
    }

    @Nested
    class Reading {

        @Test
        void aPublishedPostIsReadableByAnyone() {
            BlogPost post = post();

            assertThat(post.isReadableBy(reader)).isTrue();
            assertThat(post.isReadableBy(author)).isTrue();
        }

        @Test
        void aHiddenOrRemovedPostIsReadableOnlyByItsAuthor() {
            for (BlogPostStatus status : new BlogPostStatus[] { BlogPostStatus.HIDDEN, BlogPostStatus.REMOVED }) {
                BlogPost post = inStatus(post(), status);

                assertThat(post.isReadableBy(author)).isTrue();
                assertThat(post.isReadableBy(reader)).isFalse();
            }
        }
    }

    @Nested
    class DeletingAndInteracting {

        @Test
        void theAuthorDeletesInEveryStatusAndNobodyElseDoes() {
            for (BlogPostStatus status : BlogPostStatus.values()) {
                BlogPost post = inStatus(post(), status);

                post.ensureDeletableBy(author);
                assertThatThrownBy(() -> post.ensureDeletableBy(reader)).isInstanceOfSatisfying(
                        ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_AUTHOR"));
            }
        }

        @Test
        void onlyAPublishedPostIsOpenForInteraction() {
            post().ensureOpenForInteraction();

            for (BlogPostStatus status : new BlogPostStatus[] { BlogPostStatus.HIDDEN, BlogPostStatus.REMOVED }) {
                BlogPost post = inStatus(post(), status);

                assertThat(post.isOpenForInteraction()).isFalse();
                assertThatThrownBy(post::ensureOpenForInteraction).isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));
            }
        }

        @Test
        void aPostIsEditableOnlyByItsAuthorAndOnlyWhilePublished() {
            assertThat(post().isEditableBy(author)).isTrue();
            assertThat(post().isEditableBy(reader)).isFalse();
            assertThat(inStatus(post(), BlogPostStatus.HIDDEN).isEditableBy(author)).isFalse();
        }
    }
}
