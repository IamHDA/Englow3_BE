package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.friendship.api.FriendshipReader;

/**
 * The rule every blog use case reads a post by. The services that ask it are tested with it mocked, so what the answer
 * is - for each status, for each relationship, for an erased author - is settled here and nowhere else.
 */
class BlogReadRuleTest {

    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final BlogReadRule rule = new BlogReadRule(friendshipReader);

    private final UUID viewer = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");

    private BlogPost post(UUID authorId, BlogPostStatus status) {
        BlogPost post = BlogPost.publish(authorId, "Title", "body", Set.of(), now);
        ReflectionTestUtils.setField(post, "status", status);
        return post;
    }

    @Nested
    class ReadingAPost {

        @Test
        void aPublishedPostOfSomeoneNotBlockedIsRead() {
            assertThat(rule.canRead(post(author, BlogPostStatus.PUBLISHED), viewer)).isTrue();
        }

        @Test
        void aPublishedPostByAnAuthorWithABlockEitherWayIsNot() {
            when(friendshipReader.isBlockedEitherWay(viewer, author)).thenReturn(true);

            assertThat(rule.canRead(post(author, BlogPostStatus.PUBLISHED), viewer)).isFalse();
        }

        @Test
        void aHiddenOrRemovedPostIsNotReadBySomeoneWhoIsNotItsAuthor() {
            assertThat(rule.canRead(post(author, BlogPostStatus.HIDDEN), viewer)).isFalse();
            assertThat(rule.canRead(post(author, BlogPostStatus.REMOVED), viewer)).isFalse();
        }

        @Test
        void anAuthorReadsTheirOwnPostInEveryStatus() {
            for (BlogPostStatus status : BlogPostStatus.values()) {
                assertThat(rule.canRead(post(viewer, status), viewer)).as(status.name()).isTrue();
            }
        }

        @Test
        void anAuthorIsNeverBlockedFromTheirOwnPost() {
            assertThat(rule.canRead(post(viewer, BlogPostStatus.HIDDEN), viewer)).isTrue();

            verifyNoInteractions(friendshipReader);
        }

        @Test
        void aPostWhoseAuthorIsErasedIsReadWhilePublishedAndAsksNobodyAboutABlock() {
            assertThat(rule.canRead(post(null, BlogPostStatus.PUBLISHED), viewer)).isTrue();

            verifyNoInteractions(friendshipReader);
        }

        @Test
        void aPostWhoseAuthorIsErasedIsNobodysOnceHidden() {
            assertThat(rule.canRead(post(null, BlogPostStatus.HIDDEN), viewer)).isFalse();
            assertThat(rule.canRead(post(null, BlogPostStatus.REMOVED), viewer)).isFalse();
        }

        @Test
        void theBlockIsAskedAboutTheViewerAndTheAuthor() {
            rule.canRead(post(author, BlogPostStatus.PUBLISHED), viewer);

            verify(friendshipReader).isBlockedEitherWay(viewer, author);
        }
    }

    @Nested
    class BeingBlockedWithAnAuthor {

        @Test
        void isWhatTheFriendshipModuleSays() {
            when(friendshipReader.isBlockedEitherWay(viewer, author)).thenReturn(true);
            UUID unblocked = UUID.randomUUID();

            assertThat(rule.isBlockedWith(author, viewer)).isTrue();
            assertThat(rule.isBlockedWith(unblocked, viewer)).isFalse();
        }

        @Test
        void isNeverTrueForAnErasedAuthorOrForOneself() {
            assertThat(rule.isBlockedWith(null, viewer)).isFalse();
            assertThat(rule.isBlockedWith(viewer, viewer)).isFalse();

            verifyNoInteractions(friendshipReader);
        }
    }
}
