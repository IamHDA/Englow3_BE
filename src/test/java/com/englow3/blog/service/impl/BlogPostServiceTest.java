package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.s3.model.S3Exception;

import com.englow3.blog.dto.command.ListPostsCommand;
import com.englow3.blog.dto.command.SavePostCommand;
import com.englow3.blog.dto.projection.PostListFilter;
import com.englow3.blog.dto.projection.PostSummaryRow;
import com.englow3.blog.dto.projection.PostTagRow;
import com.englow3.blog.dto.result.BlogPostDetailResult;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.entity.BlogModerationAction;
import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostImage;
import com.englow3.blog.entity.BlogPostSort;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogSkill;
import com.englow3.blog.entity.BlogTagKind;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.query.BlogPostListQuery;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogModerationActionRepository;
import com.englow3.blog.repository.BlogPostImageRepository;
import com.englow3.blog.repository.BlogPostLikeRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.service.BlogPostService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.ObjectStorageClient.StoredObjectMetadata;
import com.englow3.shared.storage.PresignedUrlResolver;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only: what is asked in which order, what runs inside a transaction and what must not, what is deleted
 * after the commit. The rules inside {@link BlogPost} have their own test, and so has the read rule
 * ({@link BlogReadRule}, mocked here), so nothing here stubs the entity to refuse something; where a refusal comes from
 * the real entity the test is about what the service then does not do.
 */
class BlogPostServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final BlogReadRule readRule = mock(BlogReadRule.class);
    private final BlogPostRepository postRepo = mock(BlogPostRepository.class);
    private final BlogCommentRepository commentRepo = mock(BlogCommentRepository.class);
    private final BlogPostImageRepository imageRepo = mock(BlogPostImageRepository.class);
    private final BlogPostLikeRepository likeRepo = mock(BlogPostLikeRepository.class);
    private final BlogModerationActionRepository actionRepo = mock(BlogModerationActionRepository.class);
    private final BlogPostListQuery listQuery = mock(BlogPostListQuery.class);
    /** Real: it only makes summaries of rows, through the two mocks it is given. */
    private final BlogPostSummaries summaries = new BlogPostSummaries(listQuery, publicProfileReader);
    private final ObjectStorageClient objectStorage = mock(ObjectStorageClient.class);
    private final PresignedUrlResolver presignedUrls = mock(PresignedUrlResolver.class);
    /** Real: it only turns the image rows into links, through the mocks above. Its own test covers the details. */
    private final BlogImageLinks imageLinks = new BlogImageLinks(imageRepo, presignedUrls);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogPostService service = new BlogPostServiceImpl(userDirectory, friendshipReader,
            publicProfileReader, readRule, postRepo, commentRepo, imageRepo, likeRepo, actionRepo, listQuery, summaries,
            objectStorage, imageLinks, rateLimiter, transactions, clock);

    /** True only while a callback given to the transaction template is running. */
    private final AtomicBoolean inTransaction = new AtomicBoolean();
    /** How many callbacks ended in an exception, which is what makes the real template roll back. */
    private final AtomicInteger rolledBack = new AtomicInteger();
    private final AtomicReference<BlogPost> saved = new AtomicReference<>();

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "blogBucket", "blog");
        ReflectionTestUtils.setField(imageLinks, "blogBucket", "blog");
        ReflectionTestUtils.setField(imageLinks, "imageUrlTtl", Duration.ofHours(3));
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        // Whether a post may be read is BlogReadRule's to answer and has its own test; here it is open unless a test
        // says the rule refuses.
        when(readRule.canRead(any(), eq(me))).thenReturn(true);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            inTransaction.set(true);
            try {
                return ((TransactionCallback<?>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class));
            } catch (RuntimeException failure) {
                rolledBack.incrementAndGet();
                throw failure;
            } finally {
                inTransaction.set(false);
            }
        });
        // Object storage is never asked from inside a transaction: either call made from one fails the test.
        when(objectStorage.metadata(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(inTransaction).as("the bucket is asked outside any transaction").isFalse();
            return new StoredObjectMetadata(1_000, "image/png");
        });
        doAnswer(invocation -> {
            assertThat(inTransaction).as("objects are deleted after the commit").isFalse();
            return null;
        }).when(objectStorage).delete(anyString(), anyString());
        when(presignedUrls.resolve(eq("blog"), anyString(), any()))
                .thenAnswer(invocation -> "https://storage.example/get/" + invocation.getArgument(1));
        when(postRepo.saveAndFlush(any())).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(postRepo.findWithTagsById(any())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(friendshipReader.blockedEitherWay(any())).thenReturn(Set.of());
        when(publicProfileReader.profiles(any())).thenAnswer(invocation -> {
            Map<UUID, PublicProfile> profiles = new java.util.HashMap<>();
            for (UUID id : (java.util.Collection<UUID>) invocation.getArgument(0)) {
                profiles.put(id, new PublicProfile(id, "Name of " + id.toString().substring(0, 4), null));
            }
            return profiles;
        });
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private static final SavePostCommand.Tag SKILL_WRITING = new SavePostCommand.Tag(BlogTagKind.SKILL, "WRITING");

    private SavePostCommand command(String body, Long expectedVersion) {
        return new SavePostCommand("A title", body, List.of(SKILL_WRITING), expectedVersion);
    }

    private BlogPostImage image(UUID uploader, UUID postId) {
        UUID id = UUID.randomUUID();
        BlogPostImage image = BlogPostImage.issue(id, uploader, "posts/%s/%s.png".formatted(uploader, id), "image/png",
                1_000, now.minusSeconds(60));
        ReflectionTestUtils.setField(image, "postId", postId);
        return image;
    }

    private static String markdownWith(BlogPostImage... images) {
        StringBuilder body = new StringBuilder("Some text.");
        for (BlogPostImage image : images) {
            body.append("\n![picture](blog-image:").append(image.getId()).append(")");
        }
        return body.toString();
    }

    private static List<UUID> ids(BlogPostImage... images) {
        return java.util.Arrays.stream(images).map(BlogPostImage::getId).toList();
    }

    private void theseImagesExist(BlogPostImage... images) {
        when(imageRepo.findByIdIn(any())).thenReturn(List.of(images));
    }

    private BlogPost existingPost(UUID author, long version) {
        BlogPost post = BlogPost.publish(author, "Old title", "Old body", Set.of(), now.minusSeconds(3600));
        ReflectionTestUtils.setField(post, "version", version);
        return post;
    }

    private static void hide(BlogPost post) {
        ReflectionTestUtils.setField(post, "status", BlogPostStatus.HIDDEN);
    }

    private void postIsLoadable(BlogPost post) {
        when(postRepo.findWithTagsById(post.getId())).thenReturn(Optional.of(post));
        when(postRepo.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
    }

    // --- create ----------------------------------------------------------------------------------------------------

    @Nested
    class Creating {

        @Test
        void everyPostCountsAgainstThePostRateLimit() {
            service.create(command("Plain text", null));

            verify(rateLimiter).check(me, "blog-post", 5, Duration.ofMinutes(10));
        }

        @Test
        void aPostWithoutImagesIsSavedWithoutTouchingImagesOrTheBucket() {
            BlogPostDetailResult result = service.create(command("Plain text", null));

            verify(postRepo).saveAndFlush(any(BlogPost.class));
            verify(imageRepo, never()).attach(any(), anyCollection(), any());
            verifyNoInteractions(objectStorage);
            assertThat(result.title()).isEqualTo("A title");
            assertThat(result.author().userId()).isEqualTo(me);
            assertThat(result.status()).isEqualTo(BlogPostStatus.PUBLISHED);
            assertThat(result.canEdit()).isTrue();
        }

        @Test
        void anImageOfSomeoneElseIsRefusedBeforeAnythingIsSavedOrTheBucketIsAsked() {
            BlogPostImage theirs = image(other, null);
            theseImagesExist(theirs);

            assertThatThrownBy(() -> service.create(command(markdownWith(theirs), null))).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

            verify(postRepo, never()).saveAndFlush(any());
            verifyNoInteractions(objectStorage);
        }

        @Test
        void anImageThatDoesNotExistIsRefused() {
            BlogPostImage mine = image(me, null);
            when(imageRepo.findByIdIn(any())).thenReturn(List.of());

            assertThatThrownBy(() -> service.create(command(markdownWith(mine), null))).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

            verify(postRepo, never()).saveAndFlush(any());
        }

        @Test
        void anImageAlreadyInAnotherPostIsRefusedForANewPost() {
            BlogPostImage taken = image(me, UUID.randomUUID());
            theseImagesExist(taken);

            assertThatThrownBy(() -> service.create(command(markdownWith(taken), null))).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

            verify(postRepo, never()).saveAndFlush(any());
        }

        @Test
        void moreThanTenImagesAreRefusedBeforeTheDatabaseIsAsked() {
            BlogPostImage[] eleven = new BlogPostImage[11];
            for (int i = 0; i < eleven.length; i++) {
                eleven[i] = image(me, null);
            }

            assertThatThrownBy(() -> service.create(command(markdownWith(eleven), null))).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_TOO_MANY_IMAGES"));

            verifyNoInteractions(imageRepo, objectStorage);
            verify(postRepo, never()).saveAndFlush(any());
        }

        @Test
        void exactlyTenImagesAreAccepted() {
            BlogPostImage[] ten = new BlogPostImage[10];
            for (int i = 0; i < ten.length; i++) {
                ten[i] = image(me, null);
            }
            theseImagesExist(ten);
            when(imageRepo.attach(any(), anyCollection(), eq(me))).thenReturn(10);

            service.create(command(markdownWith(ten), null));

            verify(imageRepo).attach(any(), eq(ids(ten)), eq(me));
        }

        @Test
        void anImageThatWasNeverPutInTheBucketIsRefusedAndNothingIsWritten() {
            BlogPostImage mine = image(me, null);
            theseImagesExist(mine);
            when(objectStorage.metadata("blog", mine.getObjectKey()))
                    .thenThrow(S3Exception.builder().statusCode(404).message("no such key").build());

            assertThatThrownBy(() -> service.create(command(markdownWith(mine), null))).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_NOT_UPLOADED"));

            // Only the read transaction ran: no write transaction was opened.
            verify(transactions, times(1)).execute(any());
            verify(postRepo, never()).saveAndFlush(any());
        }

        @Test
        void aStoreThatFailsIsNotReportedAsAMissingImage() {
            BlogPostImage mine = image(me, null);
            theseImagesExist(mine);
            when(objectStorage.metadata("blog", mine.getObjectKey()))
                    .thenThrow(S3Exception.builder().statusCode(503).message("down").build());

            assertThatThrownBy(() -> service.create(command(markdownWith(mine), null))).isInstanceOf(S3Exception.class);

            verify(postRepo, never()).saveAndFlush(any());
        }

        @Test
        void anObjectThatIsNotTheDeclaredSizeIsRefused() {
            BlogPostImage mine = image(me, null);
            theseImagesExist(mine);
            when(objectStorage.metadata("blog", mine.getObjectKey()))
                    .thenReturn(new StoredObjectMetadata(6L * 1024 * 1024, "image/png"));

            assertThatThrownBy(() -> service.create(command(markdownWith(mine), null))).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_TOO_LARGE"));

            verify(postRepo, never()).saveAndFlush(any());
        }

        @Test
        void thePostIsSavedBeforeItsImagesAreAttachedAndTheyAreAttachedToIt() {
            BlogPostImage mine = image(me, null);
            BlogPostImage another = image(me, null);
            theseImagesExist(mine, another);
            when(imageRepo.attach(any(), anyCollection(), eq(me))).thenReturn(2);

            service.create(command(markdownWith(mine, another), null));

            InOrder order = inOrder(postRepo, imageRepo);
            order.verify(postRepo).saveAndFlush(any(BlogPost.class));
            order.verify(imageRepo).attach(saved.get().getId(), ids(mine, another), me);
            verify(objectStorage).metadata("blog", mine.getObjectKey());
            verify(objectStorage).metadata("blog", another.getObjectKey());
        }

        @Test
        void aPostIsNeverSavedWithAnImageItDidNotGet() {
            BlogPostImage mine = image(me, null);
            BlogPostImage another = image(me, null);
            theseImagesExist(mine, another);
            // Another request took one of the two between the check and the write.
            when(imageRepo.attach(any(), anyCollection(), eq(me))).thenReturn(1);

            assertThatThrownBy(() -> service.create(command(markdownWith(mine, another), null))).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

            // Thrown from inside the write transaction, so it rolls the post back.
            assertThat(rolledBack).hasValue(1);
        }

        @Test
        void theResultNamesTheImagesOfThePostWithReadLinks() {
            BlogPostImage mine = image(me, null);
            theseImagesExist(mine);
            when(imageRepo.attach(any(), anyCollection(), eq(me))).thenReturn(1);
            when(imageRepo.findByPostId(any())).thenAnswer(invocation -> {
                ReflectionTestUtils.setField(mine, "postId", invocation.getArgument(0));
                return List.of(mine);
            });

            BlogPostDetailResult result = service.create(command(markdownWith(mine), null));

            assertThat(result.images()).singleElement().satisfies(image -> {
                assertThat(image.id()).isEqualTo(mine.getId());
                assertThat(image.url()).isEqualTo("https://storage.example/get/" + mine.getObjectKey());
            });
            assertThat(result.body()).contains("blog-image:" + mine.getId());
        }
    }

    // --- update ----------------------------------------------------------------------------------------------------

    @Nested
    class Updating {

        @Test
        void everySaveCountsAgainstTheUpdateRateLimitBeforeAnythingIsRead() {
            BlogPost post = existingPost(me, 3);
            postIsLoadable(post);

            service.update(post.getId(), command("New body", 3L));

            InOrder order = inOrder(rateLimiter, postRepo);
            order.verify(rateLimiter).check(me, "blog-post-update", 20, Duration.ofMinutes(10));
            order.verify(postRepo, org.mockito.Mockito.atLeastOnce()).findWithTagsById(post.getId());
        }

        @Test
        void aSaveOverTheRateLimitIsRefusedAndNothingIsRead() {
            BlogPost post = existingPost(me, 3);
            postIsLoadable(post);
            org.mockito.Mockito
                    .doThrow(new com.englow3.shared.error.TooManyRequestsException("RATE_LIMITED", "slow down"))
                    .when(rateLimiter).check(me, "blog-post-update", 20, Duration.ofMinutes(10));

            assertThatThrownBy(() -> service.update(post.getId(), command("New body", 3L))).isInstanceOfSatisfying(
                    com.englow3.shared.error.TooManyRequestsException.class,
                    e -> assertThat(e.getCode()).isEqualTo("RATE_LIMITED"));

            verifyNoInteractions(transactions, postRepo, imageRepo);
            assertThat(post.getBody()).isEqualTo("Old body");
        }

        @Test
        void aVersionThatIsOldIsRefusedAndNothingIsWritten() {
            BlogPost post = existingPost(me, 3);
            postIsLoadable(post);

            assertThatThrownBy(() -> service.update(post.getId(), command("New body", 2L))).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_STALE"));

            verify(transactions, times(1)).execute(any());
            assertThat(post.getBody()).isEqualTo("Old body");
        }

        @Test
        void aMissingVersionIsStaleToo() {
            BlogPost post = existingPost(me, 3);
            postIsLoadable(post);

            assertThatThrownBy(() -> service.update(post.getId(), command("New body", null))).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_STALE"));
        }

        @Test
        void thePostIsChangedWhenTheVersionIsCurrent() {
            BlogPost post = existingPost(me, 3);
            postIsLoadable(post);

            BlogPostDetailResult result = service.update(post.getId(), command("New body", 3L));

            assertThat(post.getBody()).isEqualTo("New body");
            assertThat(post.getTitle()).isEqualTo("A title");
            assertThat(post.getEditedAt()).isEqualTo(now);
            assertThat(result.body()).isEqualTo("New body");
        }

        @Test
        void aPostTheReadRuleRefusesIsNotFoundAndNotChanged() {
            BlogPost post = existingPost(me, 0);
            postIsLoadable(post);
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.update(post.getId(), command("New body", 0L))).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            assertThat(post.getBody()).isEqualTo("Old body");
        }

        @Test
        void aPostThatIsGoneBeforeTheWriteIsNotFound() {
            BlogPost post = existingPost(me, 0);
            when(postRepo.findWithTagsById(post.getId())).thenReturn(Optional.of(post)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(post.getId(), command("New body", 0L))).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        }

        @Test
        void anImageTakenOutOfTheTextIsDeletedAsARowAndThenAsAnObjectAfterTheCommit() {
            BlogPost post = existingPost(me, 1);
            postIsLoadable(post);
            BlogPostImage kept = image(me, post.getId());
            BlogPostImage dropped = image(me, post.getId());
            theseImagesExist(kept);
            when(imageRepo.findByPostId(post.getId())).thenReturn(List.of(kept, dropped));

            service.update(post.getId(), command(markdownWith(kept), 1L));

            verify(imageRepo).deleteAll(List.of(dropped));
            InOrder order = inOrder(imageRepo, objectStorage);
            order.verify(imageRepo).deleteAll(List.of(dropped));
            order.verify(objectStorage).delete("blog", dropped.getObjectKey());
            verify(objectStorage, never()).delete("blog", kept.getObjectKey());
            // Already attached, so there is nothing to take and nothing to ask the bucket.
            verify(imageRepo, never()).attach(any(), anyCollection(), any());
            verify(objectStorage, never()).metadata(anyString(), anyString());
        }

        @Test
        void aNewImageIsCheckedInTheBucketThenAttachedAndOnlyTheNewOnesAreAttached() {
            BlogPost post = existingPost(me, 1);
            postIsLoadable(post);
            BlogPostImage kept = image(me, post.getId());
            BlogPostImage added = image(me, null);
            theseImagesExist(kept, added);
            when(imageRepo.findByPostId(post.getId())).thenReturn(List.of(kept));
            when(imageRepo.attach(post.getId(), List.of(added.getId()), me)).thenReturn(1);

            service.update(post.getId(), command(markdownWith(kept, added), 1L));

            verify(objectStorage).metadata("blog", added.getObjectKey());
            verify(objectStorage, never()).metadata("blog", kept.getObjectKey());
            verify(imageRepo).attach(post.getId(), List.of(added.getId()), me);
            verify(imageRepo).deleteAll(List.of());
        }

        @Test
        void aNewImageThatCouldNotBeAttachedFailsTheWriteSoThePostIsNotChanged() {
            BlogPost post = existingPost(me, 1);
            postIsLoadable(post);
            BlogPostImage added = image(me, null);
            theseImagesExist(added);
            when(imageRepo.findByPostId(post.getId())).thenReturn(List.of());
            when(imageRepo.attach(post.getId(), List.of(added.getId()), me)).thenReturn(0);

            assertThatThrownBy(() -> service.update(post.getId(), command(markdownWith(added), 1L)))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

            assertThat(rolledBack).hasValue(1);
        }

        @Test
        void anImageOfAnotherPostIsRefusedBeforeAnythingIsWritten() {
            BlogPost post = existingPost(me, 1);
            postIsLoadable(post);
            BlogPostImage elsewhere = image(me, UUID.randomUUID());
            theseImagesExist(elsewhere);

            assertThatThrownBy(() -> service.update(post.getId(), command(markdownWith(elsewhere), 1L)))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

            verify(transactions, times(1)).execute(any());
            verify(imageRepo, never()).deleteAll(anyCollection());
        }

        @Test
        void anObjectThatWillNotGoDoesNotFailTheUpdate() {
            BlogPost post = existingPost(me, 1);
            postIsLoadable(post);
            BlogPostImage dropped = image(me, post.getId());
            when(imageRepo.findByPostId(post.getId())).thenReturn(List.of(dropped));
            doAnswer(invocation -> {
                throw new IllegalStateException("store down");
            }).when(objectStorage).delete("blog", dropped.getObjectKey());

            BlogPostDetailResult result = service.update(post.getId(), command("No images now", 1L));

            assertThat(result.body()).isEqualTo("No images now");
        }

        @Test
        void thePostIsNotChangedByAnUpdateOfSomeoneElsesPost() {
            BlogPost post = existingPost(other, 1);
            postIsLoadable(post);

            assertThatThrownBy(() -> service.update(post.getId(), command("Hijacked", 1L)))
                    .isInstanceOf(com.englow3.shared.error.ForbiddenException.class);

            assertThat(post.getBody()).isEqualTo("Old body");
            verify(imageRepo, never()).deleteAll(anyCollection());
            verify(objectStorage, never()).delete(anyString(), anyString());
        }
    }

    // --- delete ----------------------------------------------------------------------------------------------------

    @Nested
    class Deleting {

        @Test
        void theKeysAreReadBeforeThePostGoesAndTheObjectsAreDeletedAfterTheCommit() {
            BlogPost post = existingPost(me, 0);
            postIsLoadable(post);
            when(imageRepo.findKeysByPostId(post.getId())).thenReturn(List.of("posts/a.png", "posts/b.png"));

            service.delete(post.getId());

            InOrder order = inOrder(imageRepo, postRepo, objectStorage);
            order.verify(imageRepo).findKeysByPostId(post.getId());
            order.verify(postRepo).delete(post);
            order.verify(objectStorage).delete("blog", "posts/a.png");
            order.verify(objectStorage).delete("blog", "posts/b.png");
        }

        /**
         * A delete that had read the post before a moderator's hide was committed would fail on the version check; the
         * locking read makes it wait for the action and remove the post as it then is. The integration test shows it
         * against a real lock.
         */
        @Test
        void thePostIsReadOnlyThroughTheLockingFinder() {
            BlogPost post = existingPost(me, 0);
            postIsLoadable(post);
            when(imageRepo.findKeysByPostId(post.getId())).thenReturn(List.of());

            service.delete(post.getId());

            InOrder order = inOrder(postRepo, imageRepo);
            order.verify(postRepo).findByIdForUpdate(post.getId());
            order.verify(imageRepo).findKeysByPostId(post.getId());
            order.verify(postRepo).delete(post);
            verify(postRepo, never()).findById(any());
        }

        @Test
        void aPostWithoutImagesDeletesNoObjects() {
            BlogPost post = existingPost(me, 0);
            postIsLoadable(post);
            when(imageRepo.findKeysByPostId(post.getId())).thenReturn(List.of());

            service.delete(post.getId());

            verify(postRepo).delete(post);
            verifyNoInteractions(objectStorage);
        }

        @Test
        void aFailureDeletingAnObjectIsNotThrownAndDoesNotStopTheOthers() {
            BlogPost post = existingPost(me, 0);
            postIsLoadable(post);
            when(imageRepo.findKeysByPostId(post.getId())).thenReturn(List.of("posts/a.png", "posts/b.png"));
            doAnswer(invocation -> {
                throw new IllegalStateException("store down");
            }).when(objectStorage).delete("blog", "posts/a.png");

            service.delete(post.getId());

            verify(postRepo).delete(post);
            verify(objectStorage).delete("blog", "posts/b.png");
        }

        @Test
        void aPostThatDoesNotExistIsNotFoundAndNothingIsRead() {
            UUID missing = UUID.randomUUID();
            when(postRepo.findByIdForUpdate(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(missing)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            verify(imageRepo, never()).findKeysByPostId(any());
            verify(postRepo, never()).delete(any());
        }

        /** Not a 403: that would tell the caller the post exists, which the read rule says they must not learn. */
        @Test
        void aPostTheReadRuleRefusesIsNotFoundAndLeftAloneAndItsObjectsToo() {
            BlogPost post = existingPost(other, 0);
            postIsLoadable(post);
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.delete(post.getId())).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            verify(imageRepo, never()).findKeysByPostId(any());
            verify(postRepo, never()).delete(any());
            verifyNoInteractions(objectStorage);
        }

        @Test
        void someoneElsesPostTheCallerCanReadIsRefusedAsNotTheirsAndLeftAloneAndItsObjectsToo() {
            BlogPost post = existingPost(other, 0);
            postIsLoadable(post);

            assertThatThrownBy(() -> service.delete(post.getId())).isInstanceOfSatisfying(
                    com.englow3.shared.error.ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_AUTHOR"));

            verify(postRepo, never()).delete(any());
            verifyNoInteractions(objectStorage);
        }
    }

    // --- get -------------------------------------------------------------------------------------------------------

    @Nested
    class Reading {

        @Test
        void aPostThatDoesNotExistIsNotFound() {
            UUID missing = UUID.randomUUID();
            when(postRepo.findWithTagsById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.get(missing)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        }

        @Test
        void aPostTheReadRuleRefusesIsNotFoundAndNothingElseIsRead() {
            BlogPost post = existingPost(other, 0);
            postIsLoadable(post);
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.get(post.getId())).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            verifyNoInteractions(likeRepo, commentRepo, publicProfileReader);
        }

        @Test
        void aHiddenPostIsReadByItsAuthorWithItsStatusAndNothingOpen() {
            BlogPost post = existingPost(me, 4);
            hide(post);
            postIsLoadable(post);

            BlogPostDetailResult result = service.get(post.getId());

            assertThat(result.status()).isEqualTo(BlogPostStatus.HIDDEN);
            assertThat(result.canEdit()).isFalse();
            assertThat(result.canInteract()).isFalse();
            assertThat(result.version()).isEqualTo(4);
        }

        @Test
        void aPublishedPostCarriesCountsLikesAndWhatTheCallerMayDo() {
            BlogPost post = existingPost(other, 7);
            postIsLoadable(post);
            when(likeRepo.countByIdPostId(post.getId())).thenReturn(12L);
            when(likeRepo.existsByIdPostIdAndIdUserId(post.getId(), me)).thenReturn(true);
            when(commentRepo.countByPostIdAndStatus(post.getId(), com.englow3.blog.entity.BlogCommentStatus.VISIBLE))
                    .thenReturn(5L);

            BlogPostDetailResult result = service.get(post.getId());

            assertThat(result.likeCount()).isEqualTo(12);
            assertThat(result.likedByMe()).isTrue();
            assertThat(result.commentCount()).isEqualTo(5);
            assertThat(result.canEdit()).isFalse();
            assertThat(result.canInteract()).isTrue();
            assertThat(result.version()).isEqualTo(7);
            assertThat(result.author().userId()).isEqualTo(other);
            verify(publicProfileReader, times(1)).profiles(Set.of(other));
        }

        @Test
        void theAuthorOfAPublishedPostMayEditIt() {
            BlogPost post = existingPost(me, 0);
            postIsLoadable(post);

            assertThat(service.get(post.getId()).canEdit()).isTrue();
        }

        @Test
        void aPostWhoseAuthorIsErasedHasNoAuthorAndNobodyIsAskedForTheirProfile() {
            BlogPost orphan = BlogPost.publish(null, "Title", "body", Set.of(), now);
            postIsLoadable(orphan);

            BlogPostDetailResult result = service.get(orphan.getId());

            assertThat(result.author()).isNull();
            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void theBodyIsReturnedAsWrittenWithALinkForEachImage() {
            BlogPost post = existingPost(other, 0);
            BlogPostImage first = image(other, post.getId());
            ReflectionTestUtils.setField(post, "body", markdownWith(first));
            postIsLoadable(post);
            when(imageRepo.findByPostId(post.getId())).thenReturn(List.of(first));

            BlogPostDetailResult result = service.get(post.getId());

            assertThat(result.body()).isEqualTo(markdownWith(first));
            assertThat(result.images()).extracting(image -> image.id()).containsExactly(first.getId());
            verify(presignedUrls).resolve("blog", first.getObjectKey(), Duration.ofHours(3));
        }

        @Test
        void theAuthorOfAHiddenPostSeesTheReasonOfTheLastHideOrRemoval() {
            BlogPost post = existingPost(me, 4);
            hide(post);
            postIsLoadable(post);
            UUID moderator = UUID.randomUUID();
            when(actionRepo.findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(any(), any(), any()))
                    .thenReturn(Optional.of(BlogModerationAction.record(BlogTargetType.POST, post.getId(),
                            BlogModerationActionType.HIDE, "Links to a shop", moderator, null, now)));

            BlogPostDetailResult result = service.get(post.getId());

            assertThat(result.moderationNote()).isEqualTo("Links to a shop");
            // Only hiding and removing are reasons a post is down: not a dismissal, not a restore.
            verify(actionRepo).findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(
                    BlogTargetType.POST, post.getId(),
                    List.of(BlogModerationActionType.HIDE, BlogModerationActionType.REMOVE));
        }

        @Test
        void aHiddenPostWithNoRecordedActionHasNoNote() {
            BlogPost post = existingPost(me, 4);
            hide(post);
            postIsLoadable(post);
            when(actionRepo.findFirstByTargetTypeAndTargetIdAndActionInOrderByCreatedAtDescIdDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(service.get(post.getId()).moderationNote()).isNull();
        }

        @Test
        void aPublishedPostHasNoNoteAndNobodyIsAskedForOne() {
            BlogPost post = existingPost(me, 0);
            postIsLoadable(post);

            assertThat(service.get(post.getId()).moderationNote()).isNull();

            verifyNoInteractions(actionRepo);
        }

        /** Only the author gets a post that is not published; the note is theirs alone, whatever the rule would say. */
        @Test
        void aReaderWhoIsNotTheAuthorIsNeverGivenTheNote() {
            BlogPost post = existingPost(other, 0);
            hide(post);
            postIsLoadable(post);

            assertThat(service.get(post.getId()).moderationNote()).isNull();

            verifyNoInteractions(actionRepo);
        }
    }

    // --- list ------------------------------------------------------------------------------------------------------

    @Nested
    class Listing {

        private final PageRequest firstPage = PageRequest.of(0, 20);

        private ListPostsCommand noFilters() {
            return new ListPostsCommand(null, null, null, null, null, null);
        }

        private PostListFilter filterPassedToTheQuery() {
            ArgumentCaptor<PostListFilter> filter = ArgumentCaptor.forClass(PostListFilter.class);
            verify(listQuery).list(filter.capture());
            return filter.getValue();
        }

        private PostSummaryRow row(UUID author, String bodyHead) {
            return new PostSummaryRow(UUID.randomUUID(), author, "Title", BlogPostStatus.PUBLISHED, bodyHead, now, null,
                    3, 2, true, true);
        }

        @Test
        void aSearchOfOneCharacterIsRefusedBeforeAnythingIsAsked() {
            assertThatThrownBy(() -> service.list(new ListPostsCommand(" a ", null, null, null, null, null), firstPage))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            e -> assertThat(e.getCode()).isEqualTo("SEARCH_TERM_TOO_SHORT"));

            verifyNoInteractions(listQuery);
        }

        @Test
        void aBlankSearchIsNoSearchAndTwoCharactersAreEnough() {
            service.list(new ListPostsCommand("   ", null, null, null, null, null), firstPage);
            service.list(new ListPostsCommand("  ab ", null, null, null, null, null), firstPage);

            ArgumentCaptor<PostListFilter> filter = ArgumentCaptor.forClass(PostListFilter.class);
            verify(listQuery, times(2)).list(filter.capture());
            assertThat(filter.getAllValues()).extracting(PostListFilter::titleTerm).containsExactly(null, "ab");
        }

        @Test
        void anAuthorListingTheirOwnPostsAsksForEveryStatus() {
            service.list(new ListPostsCommand(null, null, null, null, me, null), firstPage);

            assertThat(filterPassedToTheQuery().includesEveryStatus()).isTrue();
        }

        @Test
        void listingSomeoneElsesPostsOrNoOneInParticularAsksForPublishedOnly() {
            service.list(new ListPostsCommand(null, null, null, null, other, null), firstPage);
            service.list(noFilters(), firstPage);

            ArgumentCaptor<PostListFilter> filter = ArgumentCaptor.forClass(PostListFilter.class);
            verify(listQuery, times(2)).list(filter.capture());
            assertThat(filter.getAllValues()).extracting(PostListFilter::includesEveryStatus).containsExactly(false,
                    false);
        }

        @Test
        void theFilterCarriesTheCallersBlocksTheTagsTheSortAndTheCaller() {
            UUID blocked = UUID.randomUUID();
            when(friendshipReader.blockedEitherWay(me)).thenReturn(Set.of(blocked));

            service.list(
                    new ListPostsCommand("grammar", BlogSkill.GRAMMAR, com.englow3.blog.entity.BlogLevel.B2,
                            com.englow3.blog.entity.BlogCertificate.IELTS, null, BlogPostSort.MOST_LIKED),
                    PageRequest.of(2, 10));

            PostListFilter filter = filterPassedToTheQuery();
            assertThat(filter.viewerId()).isEqualTo(me);
            assertThat(filter.hiddenAuthors()).containsExactly(blocked);
            assertThat(filter.titleTerm()).isEqualTo("grammar");
            assertThat(filter.skill()).isEqualTo(BlogSkill.GRAMMAR);
            assertThat(filter.level()).isEqualTo(com.englow3.blog.entity.BlogLevel.B2);
            assertThat(filter.certificate()).isEqualTo(com.englow3.blog.entity.BlogCertificate.IELTS);
            assertThat(filter.sort()).isEqualTo(BlogPostSort.MOST_LIKED);
            assertThat(filter.limit()).isEqualTo(10);
            assertThat(filter.offset()).isEqualTo(20);
        }

        @Test
        void theSortDefaultsToNewest() {
            service.list(noFilters(), firstPage);

            assertThat(filterPassedToTheQuery().sort()).isEqualTo(BlogPostSort.NEWEST);
        }

        @Test
        void aPageLargerThanFiftyIsCutToFifty() {
            service.list(noFilters(), PageRequest.of(0, 500));

            assertThat(filterPassedToTheQuery().limit()).isEqualTo(50);
        }

        @Test
        void blocksProfilesAndTagsAreAskedOncePerPageNotOncePerPost() {
            UUID authorA = UUID.randomUUID();
            UUID authorB = UUID.randomUUID();
            PostSummaryRow one = row(authorA, "one");
            PostSummaryRow two = row(authorB, "two");
            PostSummaryRow three = row(authorA, "three");
            when(listQuery.list(any())).thenReturn(List.of(one, two, three));

            service.list(noFilters(), firstPage);

            verify(friendshipReader, times(1)).blockedEitherWay(me);
            verify(publicProfileReader, times(1)).profiles(Set.of(authorA, authorB));
            verify(listQuery, times(1)).tagsOf(Set.of(one.id(), two.id(), three.id()));
        }

        @Test
        void aPageWithNoPostsAsksNobodyForProfiles() {
            when(listQuery.list(any())).thenReturn(List.of());

            Page<BlogPostSummaryResult> page = service.list(noFilters(), firstPage);

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void eachSummaryHasItsExcerptAuthorTagsAndStatus() {
            UUID tagged = UUID.randomUUID();
            PostSummaryRow withTags = new PostSummaryRow(tagged, other, "Tips", BlogPostStatus.HIDDEN,
                    "# Tips\n\nSome **bold** text ![d](blog-image:6f1c2a40-1b2c-4d3e-8f90-a1b2c3d4e5f6) end", now, now,
                    9, 4, false, true);
            when(listQuery.list(any())).thenReturn(List.of(withTags));
            when(listQuery.tagsOf(any())).thenReturn(List.of(new PostTagRow(tagged, BlogTagKind.SKILL, "WRITING"),
                    new PostTagRow(tagged, BlogTagKind.CERTIFICATE, "IELTS"),
                    new PostTagRow(tagged, BlogTagKind.LEVEL, "B2")));

            BlogPostSummaryResult summary = service.list(noFilters(), firstPage).getContent().get(0);

            assertThat(summary.excerpt()).isEqualTo("Tips Some bold text end");
            assertThat(summary.author().userId()).isEqualTo(other);
            assertThat(summary.status()).isEqualTo(BlogPostStatus.HIDDEN);
            assertThat(summary.tags()).extracting(BlogTagResult::kind).containsExactly(BlogTagKind.SKILL,
                    BlogTagKind.LEVEL, BlogTagKind.CERTIFICATE);
            assertThat(summary.likeCount()).isEqualTo(9);
            assertThat(summary.commentCount()).isEqualTo(4);
            assertThat(summary.likedByMe()).isFalse();
            assertThat(summary.savedByMe()).isTrue();
        }

        @Test
        void aPostOfAnErasedAuthorHasNoAuthor() {
            when(listQuery.list(any())).thenReturn(List.of(row(null, "left behind")));

            BlogPostSummaryResult summary = service.list(noFilters(), firstPage).getContent().get(0);

            assertThat(summary.author()).isNull();
            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void aPostWhoseAuthorHasJustGoneHasNoAuthorEither() {
            when(listQuery.list(any())).thenReturn(List.of(row(other, "text")));
            org.mockito.Mockito.doReturn(Map.of()).when(publicProfileReader).profiles(any());

            assertThat(service.list(noFilters(), firstPage).getContent().get(0).author()).isNull();
        }
    }

    @Test
    void theTagCatalogueListsEveryValueOfEveryKind() {
        assertThat(service.tagCatalogue().skills()).containsExactly("LISTENING", "SPEAKING", "READING", "WRITING",
                "VOCABULARY", "GRAMMAR");
        assertThat(service.tagCatalogue().levels()).containsExactly("A1", "A2", "B1", "B2", "C1", "C2");
        assertThat(service.tagCatalogue().certificates()).containsExactly("IELTS", "TOEIC");
    }
}
