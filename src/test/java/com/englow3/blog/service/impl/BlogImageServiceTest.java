package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.blog.dto.command.StartImageUploadCommand;
import com.englow3.blog.dto.result.BlogImageUploadTicket;
import com.englow3.blog.entity.BlogPostImage;
import com.englow3.blog.repository.BlogPostImageRepository;
import com.englow3.blog.service.BlogImageService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.api.UserDirectory;

class BlogImageServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final BlogPostImageRepository imageRepo = mock(BlogPostImageRepository.class);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogImageService service = new BlogImageServiceImpl(userDirectory, imageRepo, objectStorage,
            rateLimiter, transactions, clock);

    private final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "blogBucket", "blog");
        ReflectionTestUtils.setField(service, "uploadUrlTtl", Duration.ofMinutes(10));
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0))
                .doInTransaction(mock(TransactionStatus.class)));
        when(imageRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStorage.presignPut(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(URI.create("https://storage.example/put").toURL());
    }

    @Nested
    class StartingAnUpload {

        private StartImageUploadCommand command() {
            return new StartImageUploadCommand("image/png", 123_456);
        }

        @Test
        void aTypeOrSizeThatIsNotAcceptedIsRefusedBeforeAnythingElseIsAsked() {
            assertThatThrownBy(() -> service.startUpload(new StartImageUploadCommand("image/gif", 10)))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_TYPE_NOT_ALLOWED"));
            assertThatThrownBy(() -> service.startUpload(new StartImageUploadCommand("image/png", 6L * 1024 * 1024)))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_TOO_LARGE"));

            verifyNoInteractions(imageRepo, rateLimiter);
            verify(objectStorage, never()).presignPut(anyString(), anyString(), anyString(), anyLong(), any());
        }

        @Test
        void everyStartCountsAgainstTheImageUploadRateLimit() {
            service.startUpload(command());

            verify(rateLimiter).check(me, "blog-image-upload", 20, Duration.ofMinutes(10));
        }

        @Test
        void theKeyRecordedIsTheKeyThatWasSignedAndTheImageIsNobodysPostYet() {
            BlogImageUploadTicket ticket = service.startUpload(command());

            ArgumentCaptor<BlogPostImage> saved = ArgumentCaptor.forClass(BlogPostImage.class);
            verify(imageRepo).save(saved.capture());
            ArgumentCaptor<String> signedKey = ArgumentCaptor.forClass(String.class);
            verify(objectStorage).presignPut(eq("blog"), signedKey.capture(), eq("image/png"), eq(123_456L),
                    eq(Duration.ofMinutes(10)));
            assertThat(signedKey.getValue()).isEqualTo(saved.getValue().getObjectKey());
            assertThat(signedKey.getValue()).startsWith("posts/" + me + "/").endsWith(".png");
            assertThat(saved.getValue().getId()).isEqualTo(ticket.imageId());
            assertThat(saved.getValue().getUploaderId()).isEqualTo(me);
            assertThat(saved.getValue().getPostId()).isNull();
            assertThat(saved.getValue().getContentType()).isEqualTo("image/png");
            assertThat(saved.getValue().getSizeBytes()).isEqualTo(123_456);
            assertThat(saved.getValue().getCreatedAt()).isEqualTo(now);
        }

        @Test
        void theTicketTellsWhereToPutTheFileAndWhatToWriteInTheMarkdown() {
            BlogImageUploadTicket ticket = service.startUpload(command());

            assertThat(ticket.uploadUrl()).isEqualTo("https://storage.example/put");
            assertThat(ticket.contentType()).isEqualTo("image/png");
            assertThat(ticket.expiresInSeconds()).isEqualTo(600);
            assertThat(ticket.reference()).isEqualTo("blog-image:" + ticket.imageId());
        }
    }

    @Nested
    class RemovingUnattachedImages {

        private BlogPostImage stale(String key) {
            return BlogPostImage.issue(UUID.randomUUID(), me, key, "image/png", 10, now.minus(Duration.ofHours(30)));
        }

        @Test
        void looksOnlyAtImagesOlderThanADay() {
            when(imageRepo.findTop100ByPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any())).thenReturn(List.of());

            assertThat(service.removeUnattached()).isZero();

            verify(imageRepo)
                    .findTop100ByPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(now.minus(Duration.ofHours(24)));
        }

        @Test
        void removesTheObjectOnlyOfARowThisCallActuallyDeleted() {
            BlogPostImage mine = stale("posts/u/mine.png");
            BlogPostImage takenByAPost = stale("posts/u/taken.png");
            when(imageRepo.findTop100ByPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(mine, takenByAPost)).thenReturn(List.of());
            when(imageRepo.deleteIfUnattached(mine.getId())).thenReturn(1);
            // A post attached this one after it was read, so the conditional delete finds nothing to remove.
            when(imageRepo.deleteIfUnattached(takenByAPost.getId())).thenReturn(0);

            assertThat(service.removeUnattached()).isEqualTo(1);

            verify(objectStorage).delete("blog", "posts/u/mine.png");
            verify(objectStorage, never()).delete("blog", "posts/u/taken.png");
        }

        @Test
        void anObjectThatWillNotGoDoesNotStopTheRest() {
            BlogPostImage first = stale("posts/u/1.png");
            BlogPostImage second = stale("posts/u/2.png");
            when(imageRepo.findTop100ByPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any()))
                    .thenReturn(List.of(first, second)).thenReturn(List.of());
            when(imageRepo.deleteIfUnattached(any())).thenReturn(1);
            doAnswer(invocation -> {
                throw new IllegalStateException("store down");
            }).when(objectStorage).delete("blog", "posts/u/1.png");

            assertThat(service.removeUnattached()).isEqualTo(2);

            verify(objectStorage).delete("blog", "posts/u/2.png");
        }

        @Test
        void keepsGoingWhileABatchRemovedSomethingAndStopsWhenItRemovedNothing() {
            BlogPostImage a = stale("posts/u/a.png");
            BlogPostImage b = stale("posts/u/b.png");
            when(imageRepo.findTop100ByPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(any())).thenReturn(List.of(a))
                    .thenReturn(List.of(b)).thenReturn(List.of());
            when(imageRepo.deleteIfUnattached(any())).thenReturn(1);

            assertThat(service.removeUnattached()).isEqualTo(2);
        }
    }
}
