package com.englow3.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import software.amazon.awssdk.services.s3.model.S3Exception;

import com.englow3.blog.dto.command.ListPostsCommand;
import com.englow3.blog.dto.command.SaveCommentCommand;
import com.englow3.blog.dto.command.SavePostCommand;
import com.englow3.blog.dto.command.StartImageUploadCommand;
import com.englow3.blog.dto.result.BlogCommentResult;
import com.englow3.blog.dto.result.BlogImageUploadTicket;
import com.englow3.blog.dto.result.BlogPostDetailResult;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.BlogRecommendationResult;
import com.englow3.blog.entity.BlogCertificate;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogLevel;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostSort;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogRecommendationKind;
import com.englow3.blog.entity.BlogSkill;
import com.englow3.blog.entity.BlogTagKind;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.service.BlogCommentService;
import com.englow3.blog.service.BlogImageService;
import com.englow3.blog.service.BlogLikeService;
import com.englow3.blog.service.BlogPostService;
import com.englow3.blog.service.BlogRecommendationService;
import com.englow3.blog.service.BlogSaveService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.persistence.SqlTime;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.shared.storage.ObjectStorageClient.StoredObjectMetadata;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * What a mock cannot show: the hand-written list query against real rows, the cascades and {@code set null} of erasure,
 * the conditional image attach, the check constraints, and Hibernate's {@code @Version} on a post with a tag
 * collection. Object storage is a mock - nothing here has a bucket - so the tests assert which keys would be signed,
 * checked and deleted. Rows are made with SQL where the test needs a state that is simpler to set than to reach (a
 * hidden post; moderation has its own test, {@code BlogModerationIntegrationTest}), and every test searches by a title
 * marker of its own, so tests sharing one database never see each other's posts.
 */
class BlogIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    BlogPostService postService;
    @Autowired
    BlogCommentService commentService;
    @Autowired
    BlogLikeService likeService;
    @Autowired
    BlogImageService imageService;
    @Autowired
    BlogSaveService saveService;
    @Autowired
    BlogRecommendationService recommendationService;
    @Autowired
    BlogPostRepository postRepo;
    @Autowired
    TransactionTemplate transactions;

    @MockitoBean
    ObjectStorageClient objectStorage;

    private LearnerFixture fixture;
    /** In every title of this test's posts, so a search for it finds these and no one else's. */
    private String marker;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LearnerFixture(jdbc);
        marker = "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        when(objectStorage.presignPut(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(URI.create("https://storage.example/put").toURL());
        when(objectStorage.presignGet(anyString(), anyString(), any())).thenAnswer(
                invocation -> URI.create("https://storage.example/get/" + invocation.getArgument(1)).toURL());
        when(objectStorage.metadata(anyString(), anyString())).thenReturn(new StoredObjectMetadata(1_000, "image/png"));
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private UUID insertPost(UUID author, String title, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into blog_posts (id, author_id, title, body, status, created_at, updated_at)
                values (:id, :author, :title, :body, :status, :at, :at)
                """).param("id", id).param("author", author).param("title", title).param("body", "Body of " + title)
                .param("status", status).param("at", SqlTime.at(createdAt)).update();
        return id;
    }

    private UUID insertPost(UUID author, String title) {
        return insertPost(author, title, "PUBLISHED", Instant.now());
    }

    private void tag(UUID post, String kind, String value) {
        jdbc.sql("insert into blog_post_tags (post_id, kind, tag_value) values (:post, :kind, :value)")
                .param("post", post).param("kind", kind).param("value", value).update();
    }

    private void like(UUID post, UUID user) {
        jdbc.sql("insert into blog_post_likes (post_id, user_id) values (:post, :user)").param("post", post)
                .param("user", user).update();
    }

    private UUID insertComment(UUID post, UUID author, String body, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into blog_comments (id, post_id, author_id, body, status, created_at)
                values (:id, :post, :author, :body, :status, :at)
                """).param("id", id).param("post", post).param("author", author).param("body", body)
                .param("status", status).param("at", SqlTime.at(createdAt)).update();
        return id;
    }

    private void block(UUID blocker, UUID blocked) {
        jdbc.sql("insert into user_blocks (blocker_id, blocked_id) values (:a, :b)").param("a", blocker)
                .param("b", blocked).update();
    }

    private void setStatus(UUID post, String status) {
        jdbc.sql("update blog_posts set status = :status, version = version + 1 where id = :id").param("status", status)
                .param("id", post).update();
    }

    private long count(String sql, Object... namedParams) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        for (int i = 0; i < namedParams.length; i += 2) {
            spec = spec.param((String) namedParams[i], namedParams[i + 1]);
        }
        return spec.query(Long.class).single();
    }

    private void save(UUID post, UUID user, Instant at) {
        jdbc.sql("insert into blog_post_saves (post_id, user_id, created_at) values (:post, :user, :at)")
                .param("post", post).param("user", user).param("at", SqlTime.at(at)).update();
    }

    private void likeAt(UUID post, UUID user, Instant at) {
        jdbc.sql("insert into blog_post_likes (post_id, user_id, created_at) values (:post, :user, :at)")
                .param("post", post).param("user", user).param("at", SqlTime.at(at)).update();
    }

    /** What the learner declared when they onboarded: the profile the recommendations read, through the user module. */
    private void declare(UUID user, String level, String certificate, String... skills) {
        jdbc.sql("""
                insert into learner_profiles (id, user_id, current_level, target_certificate_type)
                values (:id, :user, :level, :certificate)
                """).param("id", UUID.randomUUID()).param("user", user).param("level", level)
                .param("certificate", certificate).update();
        for (String skill : skills) {
            jdbc.sql("insert into user_target_skills (user_id, skill) values (:user, :skill)").param("user", user)
                    .param("skill", skill).update();
        }
    }

    private List<UUID> savedIds(UUID as) {
        SignedIn.as(jdbc, as);
        return saveService.listSaved(PageRequest.of(0, 50)).getContent().stream().map(BlogPostSummaryResult::id)
                .toList();
    }

    private List<UUID> recommendedIds(UUID as) {
        SignedIn.as(jdbc, as);
        return recommendationService.recommend(10).stream().map(result -> result.post().id()).toList();
    }

    private static SavePostCommand.Tag skill(BlogSkill skill) {
        return new SavePostCommand.Tag(BlogTagKind.SKILL, skill.name());
    }

    private static SavePostCommand.Tag level(BlogLevel level) {
        return new SavePostCommand.Tag(BlogTagKind.LEVEL, level.name());
    }

    private SavePostCommand command(String title, String body, Long version, SavePostCommand.Tag... tags) {
        return new SavePostCommand(title, body, List.of(tags), version);
    }

    private BlogPostDetailResult create(UUID as, String title, String body, SavePostCommand.Tag... tags) {
        SignedIn.as(jdbc, as);
        return postService.create(command(title, body, null, tags));
    }

    private BlogImageUploadTicket upload(UUID as) {
        SignedIn.as(jdbc, as);
        return imageService.startUpload(new StartImageUploadCommand("image/png", 1_000));
    }

    private String keyOf(UUID imageId) {
        return jdbc.sql("select object_key from blog_post_images where id = :id").param("id", imageId)
                .query(String.class).single();
    }

    private UUID postOfImage(UUID imageId) {
        // A list, not single(): an image no post has taken has a null post_id, which single() refuses to return.
        return jdbc.sql("select post_id from blog_post_images where id = :id").param("id", imageId)
                .query((rs, row) -> rs.getObject("post_id", UUID.class)).list().get(0);
    }

    /** Posts whose title holds this test's marker, as seen by {@code as}, with the other filters given. */
    private List<BlogPostSummaryResult> list(UUID as, String q, BlogSkill skill, BlogLevel level,
            BlogCertificate certificate, UUID authorId, BlogPostSort sort) {
        SignedIn.as(jdbc, as);
        return postService
                .list(new ListPostsCommand(q, skill, level, certificate, authorId, sort), PageRequest.of(0, 50))
                .getContent();
    }

    private List<BlogPostSummaryResult> list(UUID as) {
        return list(as, marker, null, null, null, null, null);
    }

    private List<String> titles(List<BlogPostSummaryResult> posts) {
        return posts.stream().map(BlogPostSummaryResult::title).toList();
    }

    // --- writing a post --------------------------------------------------------------------------------------------

    @Test
    void aPostWithTwoTagsAndOneImageIsSavedWithTheTagsAndTheImageAttached() {
        UUID author = fixture.learner("Writer");
        BlogImageUploadTicket ticket = upload(author);

        BlogPostDetailResult created = create(author, marker + " with an image",
                "See ![chart](" + ticket.reference() + ")", skill(BlogSkill.WRITING), level(BlogLevel.B2));

        assertThat(count("select count(*) from blog_post_tags where post_id = :p", "p", created.id())).isEqualTo(2);
        assertThat(postOfImage(ticket.imageId())).isEqualTo(created.id());
        assertThat(created.status()).isEqualTo(BlogPostStatus.PUBLISHED);
        assertThat(created.version()).isZero();
        assertThat(created.author().displayName()).isEqualTo("Writer");
        assertThat(created.images()).singleElement().satisfies(image -> {
            assertThat(image.id()).isEqualTo(ticket.imageId());
            assertThat(image.url()).isEqualTo("https://storage.example/get/" + keyOf(ticket.imageId()));
        });
        verify(objectStorage).metadata("blog", keyOf(ticket.imageId()));
        assertThat(created.tags()).extracting(tag -> tag.kind() + ":" + tag.value()).containsExactly("SKILL:WRITING",
                "LEVEL:B2");
    }

    @Test
    void anImageThatWasNotPutInTheBucketLeavesNoPost() {
        UUID author = fixture.learner();
        BlogImageUploadTicket ticket = upload(author);
        when(objectStorage.metadata("blog", keyOf(ticket.imageId())))
                .thenThrow(S3Exception.builder().statusCode(404).message("no such key").build());

        assertThatThrownBy(() -> create(author, marker + " no file", "![x](" + ticket.reference() + ")"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_NOT_UPLOADED"));

        assertThat(count("select count(*) from blog_posts where title like :t", "t", marker + "%")).isZero();
        assertThat(postOfImage(ticket.imageId())).isNull();
    }

    @Test
    void anImageOfSomeoneElseIsRefusedAndStaysUnattached() {
        UUID author = fixture.learner();
        UUID thief = fixture.learner();
        BlogImageUploadTicket ticket = upload(author);

        assertThatThrownBy(() -> create(thief, marker + " stolen", "![x](" + ticket.reference() + ")"))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

        assertThat(count("select count(*) from blog_posts where title like :t", "t", marker + "%")).isZero();
        assertThat(postOfImage(ticket.imageId())).isNull();
    }

    @Test
    void anImageAPostAlreadyHasCannotBeUsedByASecondPost() {
        UUID author = fixture.learner();
        BlogImageUploadTicket ticket = upload(author);
        BlogPostDetailResult first = create(author, marker + " first", "![x](" + ticket.reference() + ")");

        assertThatThrownBy(() -> create(author, marker + " second", "![x](" + ticket.reference() + ")"))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

        assertThat(postOfImage(ticket.imageId())).isEqualTo(first.id());
        assertThat(count("select count(*) from blog_posts where title like :t", "t", marker + "%")).isEqualTo(1);
    }

    /**
     * Two posts that both name an image nobody has taken yet. Both pass the checks; the second then finds the image
     * gone when it writes. Arranged without threads: while the second is asking the bucket (which it does between its
     * read and its write), the first runs to the end.
     */
    @Test
    void twoPostsRacingForOneUnattachedImageLeaveOnePostAndNoSecondRow() {
        UUID author = fixture.learner();
        BlogImageUploadTicket ticket = upload(author);
        String key = keyOf(ticket.imageId());
        AtomicBoolean firstHasRun = new AtomicBoolean();
        when(objectStorage.metadata("blog", key)).thenAnswer(invocation -> {
            if (firstHasRun.compareAndSet(false, true)) {
                create(author, marker + " winner", "![x](" + ticket.reference() + ")");
            }
            return new StoredObjectMetadata(1_000, "image/png");
        });

        assertThatThrownBy(() -> create(author, marker + " loser", "![x](" + ticket.reference() + ")"))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_IMAGE_INVALID"));

        assertThat(count("select count(*) from blog_posts where title like :t", "t", marker + "%")).isEqualTo(1);
        assertThat(count("select count(*) from blog_posts where title = :t", "t", marker + " loser")).isZero();
        assertThat(postOfImage(ticket.imageId())).isNotNull();
    }

    @Test
    void anyoneSignedInMayPostIncludingStaff() {
        UUID staff = fixture.staff("Teacher");

        BlogPostDetailResult created = create(staff, marker + " from staff", "Tips from a teacher");

        assertThat(created.author().displayName()).isEqualTo("Teacher");
        SignedIn.as(jdbc, staff);
        BlogCommentResult comment = commentService.create(created.id(), new SaveCommentCommand("A staff comment"));
        assertThat(comment.author().displayName()).isEqualTo("Teacher");
    }

    // --- editing and deleting --------------------------------------------------------------------------------------

    @Test
    void editingReplacesTheContentAndEveryTagAndMovesTheVersionOn() {
        UUID author = fixture.learner();
        BlogPostDetailResult created = create(author, marker + " v1", "first", skill(BlogSkill.WRITING),
                level(BlogLevel.B2));

        SignedIn.as(jdbc, author);
        BlogPostDetailResult edited = postService.update(created.id(),
                command(marker + " v2", "second", created.version(), skill(BlogSkill.GRAMMAR)));

        assertThat(edited.title()).isEqualTo(marker + " v2");
        assertThat(edited.body()).isEqualTo("second");
        assertThat(edited.version()).isGreaterThan(created.version());
        assertThat(edited.editedAt()).isNotNull();
        assertThat(jdbc.sql("select kind || ':' || tag_value from blog_post_tags where post_id = :p")
                .param("p", created.id()).query(String.class).list()).containsExactly("SKILL:GRAMMAR");
    }

    @Test
    void aSaveWithAnOldVersionIsRefusedAndTheModeratorsChangeSurvives() {
        UUID author = fixture.learner();
        BlogPostDetailResult opened = create(author, marker + " original", "text");
        // Moderation, by hand: it hides the post and moves the version on while the author's editor is open.
        setStatus(opened.id(), "HIDDEN");

        SignedIn.as(jdbc, author);
        assertThatThrownBy(
                () -> postService.update(opened.id(), command(marker + " edited", "new text", opened.version())))
                        .isInstanceOfSatisfying(ConflictException.class,
                                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_STALE"));

        assertThat(jdbc.sql("select status from blog_posts where id = :id").param("id", opened.id()).query(String.class)
                .single()).isEqualTo("HIDDEN");
        assertThat(jdbc.sql("select title from blog_posts where id = :id").param("id", opened.id()).query(String.class)
                .single()).isEqualTo(marker + " original");
    }

    @Test
    void aHiddenPostCannotBeEditedEvenWithTheCurrentVersion() {
        UUID author = fixture.learner();
        BlogPostDetailResult created = create(author, marker + " original", "text");
        setStatus(created.id(), "HIDDEN");
        SignedIn.as(jdbc, author);
        long currentVersion = postService.get(created.id()).version();

        assertThatThrownBy(
                () -> postService.update(created.id(), command(marker + " edited", "new text", currentVersion)))
                        .isInstanceOfSatisfying(ConflictException.class,
                                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_EDITABLE"));
    }

    /**
     * What {@code @Version} is for, on the real mapping: two saves that both loaded version 0. The first to flush wins;
     * the other is refused instead of overwriting it.
     */
    @Test
    void twoSavesThatBothLoadedTheSameVersionCannotBothSucceed() throws Exception {
        UUID author = fixture.learner();
        BlogPostDetailResult created = create(author, marker + " original", "text", skill(BlogSkill.WRITING));
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> slow = pool.submit(() -> transactions.execute(tx -> {
                BlogPost post = postRepo.findWithTagsById(created.id()).orElseThrow();
                post.edit(author, marker + " slow", "slow body", Set.of(), Instant.now());
                loaded.countDown();
                try {
                    proceed.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return postRepo.saveAndFlush(post);
            }));
            assertThat(loaded.await(30, TimeUnit.SECONDS)).isTrue();

            SignedIn.as(jdbc, author);
            postService.update(created.id(), command(marker + " fast", "fast body", created.version()));
            proceed.countDown();

            assertThatThrownBy(() -> slow.get(30, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(OptimisticLockingFailureException.class);
            assertThat(jdbc.sql("select title from blog_posts where id = :id").param("id", created.id())
                    .query(String.class).single()).isEqualTo(marker + " fast");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void takingAnImageOutOfTheTextDeletesItsRowAndItsObject() {
        UUID author = fixture.learner();
        BlogImageUploadTicket kept = upload(author);
        BlogImageUploadTicket dropped = upload(author);
        BlogPostDetailResult created = create(author, marker + " two images",
                "![a](%s) ![b](%s)".formatted(kept.reference(), dropped.reference()));
        String droppedKey = keyOf(dropped.imageId());

        SignedIn.as(jdbc, author);
        BlogPostDetailResult edited = postService.update(created.id(),
                command(marker + " one image", "![a](%s) only".formatted(kept.reference()), created.version()));

        assertThat(edited.images()).extracting(image -> image.id()).containsExactly(kept.imageId());
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", dropped.imageId())).isZero();
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", kept.imageId())).isEqualTo(1);
        verify(objectStorage).delete("blog", droppedKey);
        verify(objectStorage, never()).delete("blog", keyOf(kept.imageId()));
    }

    @Test
    void anImageAddedInAnEditIsCheckedAndAttached() {
        UUID author = fixture.learner();
        BlogPostDetailResult created = create(author, marker + " no image yet", "text");
        BlogImageUploadTicket added = upload(author);

        SignedIn.as(jdbc, author);
        BlogPostDetailResult edited = postService.update(created.id(),
                command(marker + " now with one", "text ![x](%s)".formatted(added.reference()), created.version()));

        assertThat(postOfImage(added.imageId())).isEqualTo(created.id());
        assertThat(edited.images()).hasSize(1);
        verify(objectStorage).metadata("blog", keyOf(added.imageId()));
    }

    @Test
    void deletingAPostRemovesEverythingOfItAndThenItsObjects() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        BlogImageUploadTicket ticket = upload(author);
        BlogPostDetailResult created = create(author, marker + " to delete", "![x](" + ticket.reference() + ")",
                skill(BlogSkill.READING));
        String key = keyOf(ticket.imageId());
        insertComment(created.id(), reader, "a comment", "VISIBLE", Instant.now());
        like(created.id(), reader);

        SignedIn.as(jdbc, author);
        postService.delete(created.id());

        assertThat(count("select count(*) from blog_posts where id = :id", "id", created.id())).isZero();
        assertThat(count("select count(*) from blog_post_tags where post_id = :id", "id", created.id())).isZero();
        assertThat(count("select count(*) from blog_comments where post_id = :id", "id", created.id())).isZero();
        assertThat(count("select count(*) from blog_post_likes where post_id = :id", "id", created.id())).isZero();
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", ticket.imageId())).isZero();
        verify(objectStorage).delete("blog", key);
    }

    @Test
    void anAuthorDeletesAHiddenPostAndSomeoneElseCannotDeleteAnyPost() {
        UUID author = fixture.learner();
        UUID stranger = fixture.learner();
        UUID hidden = insertPost(author, marker + " hidden", "HIDDEN", Instant.now());
        UUID published = insertPost(author, marker + " published");

        SignedIn.as(jdbc, stranger);
        // Another person's post they can read: refused as not theirs. One they cannot read does not exist for them.
        assertThatThrownBy(() -> postService.delete(published)).isInstanceOfSatisfying(
                com.englow3.shared.error.ForbiddenException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_AUTHOR"));
        assertThatThrownBy(() -> postService.delete(hidden)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        assertThatThrownBy(() -> postService.delete(UUID.randomUUID())).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        assertThat(count("select count(*) from blog_posts where title like :t", "t", marker + "%")).isEqualTo(2);

        SignedIn.as(jdbc, author);
        postService.delete(hidden);
        assertThat(count("select count(*) from blog_posts where id = :id", "id", hidden)).isZero();
    }

    // --- the list --------------------------------------------------------------------------------------------------

    @Test
    void theListFiltersByEachTagAndByAllOfThem() {
        UUID author = fixture.learner();
        UUID viewer = fixture.learner();
        UUID writingB2 = insertPost(author, marker + " writing b2");
        tag(writingB2, "SKILL", "WRITING");
        tag(writingB2, "LEVEL", "B2");
        tag(writingB2, "CERTIFICATE", "IELTS");
        UUID writingC1 = insertPost(author, marker + " writing c1");
        tag(writingC1, "SKILL", "WRITING");
        tag(writingC1, "LEVEL", "C1");
        UUID speakingB2 = insertPost(author, marker + " speaking b2");
        tag(speakingB2, "SKILL", "SPEAKING");
        tag(speakingB2, "LEVEL", "B2");
        insertPost(author, marker + " untagged");

        assertThat(titles(list(viewer, marker, BlogSkill.WRITING, null, null, null, null)))
                .containsExactlyInAnyOrder(marker + " writing b2", marker + " writing c1");
        assertThat(titles(list(viewer, marker, null, BlogLevel.B2, null, null, null)))
                .containsExactlyInAnyOrder(marker + " writing b2", marker + " speaking b2");
        assertThat(titles(list(viewer, marker, null, null, BlogCertificate.IELTS, null, null)))
                .containsExactly(marker + " writing b2");
        assertThat(titles(list(viewer, marker, BlogSkill.WRITING, BlogLevel.B2, BlogCertificate.IELTS, null, null)))
                .containsExactly(marker + " writing b2");
        assertThat(titles(list(viewer, marker, BlogSkill.SPEAKING, BlogLevel.C1, null, null, null))).isEmpty();
        assertThat(list(viewer)).hasSize(4);
    }

    @Test
    void aSummaryCarriesItsTagsInAFixedOrder() {
        UUID author = fixture.learner();
        UUID post = insertPost(author, marker + " tagged");
        tag(post, "CERTIFICATE", "TOEIC");
        tag(post, "LEVEL", "A2");
        tag(post, "SKILL", "LISTENING");
        tag(post, "SKILL", "GRAMMAR");

        BlogPostSummaryResult summary = list(fixture.learner()).get(0);

        assertThat(summary.tags()).extracting(tag -> tag.kind() + ":" + tag.value()).containsExactly("SKILL:GRAMMAR",
                "SKILL:LISTENING", "LEVEL:A2", "CERTIFICATE:TOEIC");
    }

    @Test
    void theTitleSearchIgnoresCaseAndTreatsPercentAndUnderscoreAsPlainCharacters() {
        UUID author = fixture.learner();
        UUID viewer = fixture.learner();
        insertPost(author, marker + " 100% sure");
        insertPost(author, marker + " 100 sure");
        insertPost(author, marker + " a_b");
        insertPost(author, marker + " axb");

        assertThat(titles(list(viewer, marker + " 100%", null, null, null, null, null)))
                .containsExactly(marker + " 100% sure");
        assertThat(titles(list(viewer, marker + " a_b", null, null, null, null, null)))
                .containsExactly(marker + " a_b");
        assertThat(titles(list(viewer, marker.toUpperCase() + " 100%", null, null, null, null, null)))
                .containsExactly(marker + " 100% sure");
        assertThat(list(viewer, marker + " 100", null, null, null, null, null)).hasSize(2);
    }

    @Test
    void aSearchThatIsASingleQuoteOrAnEscapeCharacterIsJustText() {
        UUID author = fixture.learner();
        insertPost(author, marker + " it's !important");

        assertThat(titles(list(fixture.learner(), marker + " it's !imp", null, null, null, null, null)))
                .containsExactly(marker + " it's !important");
    }

    @Test
    void theListOfOneAuthorHoldsThatAuthorsPostsOnly() {
        UUID author = fixture.learner();
        UUID another = fixture.learner();
        UUID viewer = fixture.learner();
        insertPost(author, marker + " by author");
        insertPost(another, marker + " by another");

        assertThat(titles(list(viewer, marker, null, null, null, author, null))).containsExactly(marker + " by author");
    }

    @Test
    void newestComesFirstAndMostLikedOrdersByLikesThenNewest() {
        UUID author = fixture.learner();
        UUID viewer = fixture.learner();
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        UUID oldest = insertPost(author, marker + " oldest", "PUBLISHED", base);
        UUID middle = insertPost(author, marker + " middle", "PUBLISHED", base.plusSeconds(60));
        UUID newest = insertPost(author, marker + " newest", "PUBLISHED", base.plusSeconds(120));
        UUID tieOlder = insertPost(author, marker + " tie older", "PUBLISHED", base.plusSeconds(30));
        UUID fans1 = fixture.learner();
        UUID fans2 = fixture.learner();
        UUID fans3 = fixture.learner();
        like(oldest, fans1);
        like(oldest, fans2);
        like(oldest, fans3);
        like(middle, fans1);
        like(tieOlder, fans1);

        assertThat(titles(list(viewer, marker, null, null, null, null, BlogPostSort.NEWEST)))
                .containsExactly(marker + " newest", marker + " middle", marker + " tie older", marker + " oldest");
        assertThat(titles(list(viewer, marker, null, null, null, null, BlogPostSort.MOST_LIKED)))
                .containsExactly(marker + " oldest", marker + " middle", marker + " tie older", marker + " newest");
        assertThat(newest).isNotNull();
    }

    @Test
    void aSummaryCountsLikesAndVisibleCommentsAndSaysWhetherTheViewerLiked() {
        UUID author = fixture.learner();
        UUID viewer = fixture.learner();
        UUID fan = fixture.learner();
        UUID post = insertPost(author, marker + " counted");
        like(post, viewer);
        like(post, fan);
        insertComment(post, fan, "one", "VISIBLE", Instant.now());
        insertComment(post, fan, "two", "VISIBLE", Instant.now());
        insertComment(post, fan, "hidden", "HIDDEN", Instant.now());
        insertComment(post, fan, "removed", "REMOVED", Instant.now());

        BlogPostSummaryResult asViewer = list(viewer).get(0);
        BlogPostSummaryResult asAuthor = list(author).get(0);

        assertThat(asViewer.likeCount()).isEqualTo(2);
        assertThat(asViewer.commentCount()).isEqualTo(2);
        assertThat(asViewer.likedByMe()).isTrue();
        assertThat(asAuthor.likedByMe()).isFalse();
    }

    @Test
    void thePageSaysHowManyPostsMatchAcrossPages() {
        UUID author = fixture.learner();
        UUID viewer = fixture.learner();
        for (int i = 0; i < 5; i++) {
            insertPost(author, marker + " post " + i, "PUBLISHED", Instant.now().minusSeconds(i));
        }

        SignedIn.as(jdbc, viewer);
        var firstPage = postService.list(new ListPostsCommand(marker, null, null, null, null, null),
                PageRequest.of(0, 2));
        var lastPage = postService.list(new ListPostsCommand(marker, null, null, null, null, null),
                PageRequest.of(2, 2));
        var pastTheEnd = postService.list(new ListPostsCommand(marker, null, null, null, null, null),
                PageRequest.of(9, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(lastPage.getContent()).hasSize(1);
        assertThat(pastTheEnd.getContent()).isEmpty();
        assertThat(pastTheEnd.getTotalElements()).isEqualTo(5);
    }

    @Test
    void anExcerptNeverShowsATruncatedImageReference() {
        UUID author = fixture.learner();
        UUID post = insertPost(author, marker + " long");
        // 1190 characters of text, then an image that the first 1200 characters cut in the middle of its reference.
        String body = "word ".repeat(238) + "![diagram](blog-image:6f1c2a40-1b2c-4d3e-8f90-a1b2c3d4e5f6) and the end";
        jdbc.sql("update blog_posts set body = :body where id = :id").param("body", body).param("id", post).update();

        BlogPostSummaryResult summary = list(fixture.learner()).get(0);

        assertThat(summary.excerpt()).doesNotContain("blog-image").doesNotContain("![").doesNotContain("](");
        assertThat(summary.excerpt().length()).isLessThanOrEqualTo(200);
    }

    // --- hidden and removed content --------------------------------------------------------------------------------

    @Test
    void aHiddenOrRemovedPostIsInNoGeneralListAndIsInItsAuthorsOwnListWithItsStatus() {
        UUID author = fixture.learner();
        UUID other = fixture.learner();
        insertPost(author, marker + " published");
        insertPost(author, marker + " hidden", "HIDDEN", Instant.now());
        insertPost(author, marker + " removed", "REMOVED", Instant.now());

        // Everyone, the author included, sees only the published one in a list that is not the author's own.
        assertThat(titles(list(other))).containsExactly(marker + " published");
        assertThat(titles(list(author))).containsExactly(marker + " published");
        // The author asking for their own posts sees all three, each with its status.
        List<BlogPostSummaryResult> own = list(author, marker, null, null, null, author, null);
        assertThat(own).extracting(BlogPostSummaryResult::title).containsExactlyInAnyOrder(marker + " published",
                marker + " hidden", marker + " removed");
        assertThat(own).extracting(BlogPostSummaryResult::status).containsExactlyInAnyOrder(BlogPostStatus.PUBLISHED,
                BlogPostStatus.HIDDEN, BlogPostStatus.REMOVED);
        // Someone else asking for the same author's posts gets the published one and nothing more.
        assertThat(titles(list(other, marker, null, null, null, author, null))).containsExactly(marker + " published");
    }

    @Test
    void theTotalOfAnAuthorsOwnListCountsEveryStatusToo() {
        UUID author = fixture.learner();
        insertPost(author, marker + " published");
        insertPost(author, marker + " hidden", "HIDDEN", Instant.now());

        SignedIn.as(jdbc, author);
        var own = postService.list(new ListPostsCommand(marker, null, null, null, author, null), PageRequest.of(1, 1));

        assertThat(own.getTotalElements()).isEqualTo(2);
        assertThat(own.getContent()).hasSize(1);
    }

    @Test
    void aHiddenPostCanBeOpenedByItsAuthorOnlyAndCannotBeInteractedWith() {
        UUID author = fixture.learner();
        UUID other = fixture.learner();
        UUID hidden = insertPost(author, marker + " hidden", "HIDDEN", Instant.now());

        SignedIn.as(jdbc, other);
        assertThatThrownBy(() -> postService.get(hidden)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        assertThatThrownBy(() -> likeService.like(hidden)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> commentService.list(hidden, PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);

        SignedIn.as(jdbc, author);
        BlogPostDetailResult own = postService.get(hidden);
        assertThat(own.status()).isEqualTo(BlogPostStatus.HIDDEN);
        assertThat(own.canEdit()).isFalse();
        assertThat(own.canInteract()).isFalse();
        assertThatThrownBy(() -> commentService.create(hidden, new SaveCommentCommand("hello"))).isInstanceOfSatisfying(
                ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));
        assertThatThrownBy(() -> likeService.like(hidden)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));
    }

    @Test
    void aHiddenOrRemovedCommentIsSeenByItsAuthorWithItsStatusAndByNoOneElse() {
        UUID postAuthor = fixture.learner();
        UUID commenter = fixture.learner();
        UUID visibleCommenter = fixture.learner();
        UUID bystander = fixture.learner();
        UUID post = insertPost(postAuthor, marker + " discussed");
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        insertComment(post, commenter, "hidden one", "HIDDEN", base);
        insertComment(post, visibleCommenter, "visible one", "VISIBLE", base.plusSeconds(10));
        insertComment(post, commenter, "removed one", "REMOVED", base.plusSeconds(20));
        insertComment(post, commenter, "visible two", "VISIBLE", base.plusSeconds(30));

        SignedIn.as(jdbc, commenter);
        List<BlogCommentResult> asCommenter = commentService.list(post, PageRequest.of(0, 20)).getContent();
        SignedIn.as(jdbc, bystander);
        List<BlogCommentResult> asBystander = commentService.list(post, PageRequest.of(0, 20)).getContent();
        SignedIn.as(jdbc, postAuthor);
        List<BlogCommentResult> asPostAuthor = commentService.list(post, PageRequest.of(0, 20)).getContent();

        assertThat(asCommenter).extracting(BlogCommentResult::body).containsExactly("hidden one", "visible one",
                "removed one", "visible two");
        assertThat(asCommenter).extracting(BlogCommentResult::status).containsExactly(BlogCommentStatus.HIDDEN,
                BlogCommentStatus.VISIBLE, BlogCommentStatus.REMOVED, BlogCommentStatus.VISIBLE);
        assertThat(asCommenter).extracting(BlogCommentResult::canEdit).containsExactly(false, false, false, true);
        assertThat(asBystander).extracting(BlogCommentResult::body).containsExactly("visible one", "visible two");
        assertThat(asPostAuthor).extracting(BlogCommentResult::body).containsExactly("visible one", "visible two");
    }

    @Test
    void aLikeCanBeTakenBackFromAPostThatWasHiddenAfterwardsButTheCountReportedIsZero() {
        UUID author = fixture.learner();
        UUID fan = fixture.learner();
        UUID anotherFan = fixture.learner();
        UUID post = insertPost(author, marker + " liked then hidden");
        like(post, fan);
        like(post, anotherFan);
        setStatus(post, "HIDDEN");

        SignedIn.as(jdbc, fan);
        var state = likeService.unlike(post);

        assertThat(state.liked()).isFalse();
        // Nothing of a post the caller cannot read is told to them, the count included: one like is still there.
        assertThat(state.likeCount()).isZero();
        assertThat(
                count("select count(*) from blog_post_likes where post_id = :p and user_id = :u", "p", post, "u", fan))
                        .isZero();
        assertThat(count("select count(*) from blog_post_likes where post_id = :p", "p", post)).isEqualTo(1);
    }

    @Test
    void anAuthorWhoseOwnPostWasHiddenSeesTheRealCountWhenTheyUnlikeIt() {
        UUID author = fixture.learner();
        UUID fan = fixture.learner();
        UUID post = insertPost(author, marker + " mine, hidden");
        like(post, author);
        like(post, fan);
        setStatus(post, "HIDDEN");

        SignedIn.as(jdbc, author);

        assertThat(likeService.unlike(post).likeCount()).isEqualTo(1);
    }

    @Test
    void aCommentCanBeDeletedByItsAuthorAlthoughThePostIsHidden() {
        UUID author = fixture.learner();
        UUID commenter = fixture.learner();
        UUID post = insertPost(author, marker + " hidden later", "HIDDEN", Instant.now());
        UUID comment = insertComment(post, commenter, "mine", "VISIBLE", Instant.now());

        SignedIn.as(jdbc, commenter);
        commentService.delete(comment);

        assertThat(count("select count(*) from blog_comments where id = :id", "id", comment)).isZero();
    }

    // --- likes and comments ----------------------------------------------------------------------------------------

    @Test
    void likingTwiceIsOneLikeAndAnotherPersonsLikeAndAnUnlikeCountRight() {
        UUID author = fixture.learner();
        UUID fan = fixture.learner();
        UUID other = fixture.learner();
        UUID post = insertPost(author, marker + " likable");

        SignedIn.as(jdbc, fan);
        likeService.like(post);
        var again = likeService.like(post);
        assertThat(again.liked()).isTrue();
        assertThat(again.likeCount()).isEqualTo(1);
        assertThat(count("select count(*) from blog_post_likes where post_id = :p", "p", post)).isEqualTo(1);

        SignedIn.as(jdbc, other);
        assertThat(likeService.like(post).likeCount()).isEqualTo(2);
        var afterUnlike = likeService.unlike(post);
        assertThat(afterUnlike.liked()).isFalse();
        assertThat(afterUnlike.likeCount()).isEqualTo(1);
        // Unliking something never liked changes nothing and is not an error.
        assertThat(likeService.unlike(post).likeCount()).isEqualTo(1);

        SignedIn.as(jdbc, fan);
        assertThat(postService.get(post).likedByMe()).isTrue();
        assertThat(postService.get(post).likeCount()).isEqualTo(1);
    }

    @Test
    void commentsAreListedOldestFirstAcrossPages() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        UUID post = insertPost(author, marker + " discussed");
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        for (int i = 0; i < 5; i++) {
            insertComment(post, reader, "comment " + i, "VISIBLE", base.plusSeconds(i));
        }

        SignedIn.as(jdbc, reader);
        var firstPage = commentService.list(post, PageRequest.of(0, 2));
        var lastPage = commentService.list(post, PageRequest.of(2, 2));

        assertThat(firstPage.getContent()).extracting(BlogCommentResult::body).containsExactly("comment 0",
                "comment 1");
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(lastPage.getContent()).extracting(BlogCommentResult::body).containsExactly("comment 4");
    }

    @Test
    void aCommentIsWrittenEditedAndDeletedByItsAuthor() {
        UUID author = fixture.learner();
        UUID commenter = fixture.learner();
        UUID post = insertPost(author, marker + " open");

        SignedIn.as(jdbc, commenter);
        BlogCommentResult written = commentService.create(post, new SaveCommentCommand("first"));
        BlogCommentResult edited = commentService.update(written.id(), new SaveCommentCommand("second"));

        assertThat(edited.body()).isEqualTo("second");
        assertThat(edited.editedAt()).isNotNull();
        assertThat(postService.get(post).commentCount()).isEqualTo(1);

        SignedIn.as(jdbc, author);
        assertThatThrownBy(() -> commentService.update(written.id(), new SaveCommentCommand("hijack")))
                .isInstanceOf(com.englow3.shared.error.ForbiddenException.class);
        assertThatThrownBy(() -> commentService.delete(written.id()))
                .isInstanceOf(com.englow3.shared.error.ForbiddenException.class);

        SignedIn.as(jdbc, commenter);
        commentService.delete(written.id());
        assertThat(count("select count(*) from blog_comments where post_id = :p", "p", post)).isZero();
    }

    // --- blocks ----------------------------------------------------------------------------------------------------

    @Test
    void aBlockInEitherDirectionHidesThePostsOfTheOtherAndRefusesEverythingOnThem() {
        UUID blocker = fixture.learner();
        UUID blocked = fixture.learner();
        UUID bystander = fixture.learner();
        UUID blockersPost = insertPost(blocker, marker + " by blocker");
        UUID blockedsPost = insertPost(blocked, marker + " by blocked");
        UUID bystandersPost = insertPost(bystander, marker + " by bystander");
        block(blocker, blocked);

        assertThat(titles(list(blocker))).containsExactly(marker + " by bystander", marker + " by blocker");
        assertThat(titles(list(blocked))).containsExactly(marker + " by bystander", marker + " by blocked");
        assertThat(titles(list(bystander))).hasSize(3);

        for (UUID[] pair : new UUID[][] { { blocker, blockedsPost }, { blocked, blockersPost } }) {
            SignedIn.as(jdbc, pair[0]);
            assertThatThrownBy(() -> postService.get(pair[1])).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
            assertThatThrownBy(() -> likeService.like(pair[1])).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> commentService.create(pair[1], new SaveCommentCommand("hi")))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> commentService.list(pair[1], PageRequest.of(0, 10)))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> postService.update(pair[1], command("t", "b", 0L)))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> postService.delete(pair[1])).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
            // Taking a like back is always allowed and says nothing about the post: the count is 0.
            assertThat(likeService.unlike(pair[1]).likeCount()).isZero();
            // The author's own list answers with nothing rather than refusing, so it reveals no block either.
            assertThat(
                    postService
                            .list(new ListPostsCommand(null, null, null, null,
                                    pair[1].equals(blockedsPost) ? blocked : blocker, null), PageRequest.of(0, 10))
                            .getContent()).isEmpty();
        }
        assertThat(bystandersPost).isNotNull();
    }

    @Test
    void aBlockHidesTheCommentsOfTheOtherPersonOnAPostBothCanStillSee() {
        UUID viewer = fixture.learner();
        UUID blockedCommenter = fixture.learner();
        UUID friendlyCommenter = fixture.learner();
        UUID post = insertPost(fixture.learner(), marker + " busy");
        insertComment(post, blockedCommenter, "from the blocked one", "VISIBLE", Instant.now().minusSeconds(20));
        insertComment(post, friendlyCommenter, "from the other one", "VISIBLE", Instant.now().minusSeconds(10));
        block(viewer, blockedCommenter);

        SignedIn.as(jdbc, viewer);
        assertThat(commentService.list(post, PageRequest.of(0, 10)).getContent()).extracting(BlogCommentResult::body)
                .containsExactly("from the other one");
        SignedIn.as(jdbc, blockedCommenter);
        assertThat(commentService.list(post, PageRequest.of(0, 10)).getContent()).extracting(BlogCommentResult::body)
                .containsExactly("from the blocked one", "from the other one");
    }

    @Test
    void aCommentCannotBeEditedOnceThePostsAuthorHasBlockedTheCommenter() {
        UUID postAuthor = fixture.learner();
        UUID commenter = fixture.learner();
        UUID post = insertPost(postAuthor, marker + " discussed");
        SignedIn.as(jdbc, commenter);
        BlogCommentResult written = commentService.create(post, new SaveCommentCommand("before the block"));
        block(postAuthor, commenter);

        assertThatThrownBy(() -> commentService.update(written.id(), new SaveCommentCommand("after the block")))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));

        assertThat(jdbc.sql("select body from blog_comments where id = :id").param("id", written.id())
                .query(String.class).single()).isEqualTo("before the block");
    }

    // --- erasure ---------------------------------------------------------------------------------------------------

    @Test
    void erasingAnAuthorKeepsTheirPostsAndCommentsAsByNoOneAndRemovesTheirLikes() {
        UUID leaver = fixture.learner("Leaver");
        UUID stayer = fixture.learner("Stayer");
        UUID leaversPost = insertPost(leaver, marker + " by leaver");
        UUID stayersPost = insertPost(stayer, marker + " by stayer");
        UUID leaversComment = insertComment(stayersPost, leaver, "from leaver", "VISIBLE", Instant.now());
        like(stayersPost, leaver);
        like(stayersPost, stayer);

        jdbc.sql("delete from users where id = :id").param("id", leaver).update();

        assertThat(count("select count(*) from blog_posts where id = :id and author_id is null", "id", leaversPost))
                .isEqualTo(1);
        assertThat(
                count("select count(*) from blog_comments where id = :id and author_id is null", "id", leaversComment))
                        .isEqualTo(1);
        assertThat(count("select count(*) from blog_post_likes where post_id = :p", "p", stayersPost)).isEqualTo(1);
        SignedIn.as(jdbc, stayer);
        assertThat(postService.get(leaversPost).author()).isNull();
        assertThat(postService.get(leaversPost).canEdit()).isFalse();
        assertThat(commentService.list(stayersPost, PageRequest.of(0, 10)).getContent()).singleElement()
                .satisfies(comment -> {
                    assertThat(comment.author()).isNull();
                    assertThat(comment.body()).isEqualTo("from leaver");
                });
        assertThat(postService.get(stayersPost).likeCount()).isEqualTo(1);
        List<BlogPostSummaryResult> listed = list(stayer);
        assertThat(listed).extracting(BlogPostSummaryResult::title).contains(marker + " by leaver");
        assertThat(
                listed.stream().filter(post -> post.title().endsWith("by leaver")).findFirst().orElseThrow().author())
                        .isNull();
    }

    @Test
    void aPostWhoseAuthorIsErasedStillShowsInAListThatHasABlockInIt() {
        UUID leaver = fixture.learner();
        UUID viewer = fixture.learner();
        UUID blockedAuthor = fixture.learner();
        insertPost(leaver, marker + " by leaver");
        insertPost(blockedAuthor, marker + " by blocked");
        insertPost(fixture.learner(), marker + " by anyone");
        jdbc.sql("update blog_posts set author_id = null where title = :t").param("t", marker + " by leaver").update();
        block(viewer, blockedAuthor);

        assertThat(titles(list(viewer))).containsExactlyInAnyOrder(marker + " by leaver", marker + " by anyone");
    }

    // --- images ----------------------------------------------------------------------------------------------------

    @Test
    void theSweepRemovesOnlyImagesNoPostTookWithinADayAndTheirObjects() {
        UUID uploader = fixture.learner();
        UUID author = fixture.learner();
        UUID post = insertPost(author, marker + " has an image");
        UUID staleUnattached = insertImage(uploader, null, Instant.now().minus(30, ChronoUnit.HOURS));
        UUID freshUnattached = insertImage(uploader, null, Instant.now().minus(1, ChronoUnit.HOURS));
        UUID staleAttached = insertImage(uploader, post, Instant.now().minus(30, ChronoUnit.HOURS));

        int removed = imageService.removeUnattached();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", staleUnattached)).isZero();
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", freshUnattached)).isEqualTo(1);
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", staleAttached)).isEqualTo(1);
        verify(objectStorage).delete("blog", "posts/%s/%s.png".formatted(uploader, staleUnattached));
        verify(objectStorage, never()).delete("blog", "posts/%s/%s.png".formatted(uploader, freshUnattached));
        verify(objectStorage, never()).delete("blog", "posts/%s/%s.png".formatted(uploader, staleAttached));
    }

    @Test
    void anUploaderWhoseAccountIsErasedLeavesTheImageOfTheirPostAndALeftoverUploadForTheSweep() {
        UUID uploader = fixture.learner();
        UUID author = fixture.learner();
        UUID post = insertPost(author, marker + " has an image");
        UUID attached = insertImage(uploader, post, Instant.now());
        UUID pending = insertImage(uploader, null, Instant.now().minus(30, ChronoUnit.HOURS));

        jdbc.sql("delete from users where id = :id").param("id", uploader).update();

        assertThat(
                count("select count(*) from blog_post_images where id = :id and uploader_id is null", "id", attached))
                        .isEqualTo(1);
        assertThat(count("select count(*) from blog_post_images where id = :id and uploader_id is null", "id", pending))
                .isEqualTo(1);
        imageService.removeUnattached();
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", pending)).isZero();
        assertThat(count("select count(*) from blog_post_images where id = :id", "id", attached)).isEqualTo(1);
    }

    private UUID insertImage(UUID uploader, UUID post, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql(
                """
                        insert into blog_post_images (id, post_id, uploader_id, object_key, content_type, size_bytes, created_at)
                        values (:id, :post, :uploader, :key, 'image/png', 1000, :at)
                        """)
                .param("id", id).param("post", post).param("uploader", uploader)
                .param("key", "posts/%s/%s.png".formatted(uploader, id)).param("at", SqlTime.at(createdAt)).update();
        return id;
    }

    // --- the schema's own rules ------------------------------------------------------------------------------------

    @Test
    void theDatabaseRefusesWhatTheEntityWouldRefuse() {
        UUID author = fixture.learner();
        UUID post = insertPost(author, marker + " valid");

        assertThatThrownBy(() -> rawPost("   ", "body", "PUBLISHED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawPost("title", "  ", "PUBLISHED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawPost("title", "x".repeat(20_001), "PUBLISHED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> rawPost("title", "body", "DELETED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertComment(post, author, "x".repeat(2_001), "VISIBLE", Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertComment(post, author, " ", "VISIBLE", Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertComment(post, author, "ok", "DELETED", Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tag(post, "TOPIC", "WRITING")).isInstanceOf(DataIntegrityViolationException.class);
        tag(post, "SKILL", "WRITING");
        assertThatThrownBy(() -> tag(post, "SKILL", "WRITING")).isInstanceOf(DataIntegrityViolationException.class);
        like(post, author);
        assertThatThrownBy(() -> like(post, author)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void rawPost(String title, String body, String status) {
        jdbc.sql(
                "insert into blog_posts (id, author_id, title, body, status) values (:id, null, :title, :body, :status)")
                .param("id", UUID.randomUUID()).param("title", title).param("body", body).param("status", status)
                .update();
    }

    // --- saved posts -----------------------------------------------------------------------------------------------

    @Test
    void savingTwiceIsOneSaveAndOnlyTheSaversSummaryShowsItSaved() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        UUID other = fixture.learner();
        UUID post = insertPost(author, marker + " keeper");

        SignedIn.as(jdbc, reader);
        saveService.save(post);
        var again = saveService.save(post);

        assertThat(again.saved()).isTrue();
        assertThat(count("select count(*) from blog_post_saves where post_id = :p", "p", post)).isEqualTo(1);
        assertThat(list(reader).get(0).savedByMe()).isTrue();
        assertThat(list(other).get(0).savedByMe()).isFalse();

        SignedIn.as(jdbc, reader);
        assertThat(saveService.unsave(post).saved()).isFalse();
        // Unsaving something never saved changes nothing and is not an error.
        assertThat(saveService.unsave(post).saved()).isFalse();
        assertThat(count("select count(*) from blog_post_saves where post_id = :p", "p", post)).isZero();
        assertThat(list(reader).get(0).savedByMe()).isFalse();
    }

    @Test
    void aHiddenPostCannotBeSavedAndAPostOfSomeoneWhoBlockedTheReaderIsNotFound() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        UUID hidden = insertPost(author, marker + " hidden", "HIDDEN", Instant.now());
        UUID blockedOut = insertPost(author, marker + " blocked out");
        block(author, reader);

        SignedIn.as(jdbc, reader);
        assertThatThrownBy(() -> saveService.save(blockedOut)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        assertThatThrownBy(() -> saveService.save(hidden)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

        SignedIn.as(jdbc, author);
        assertThatThrownBy(() -> saveService.save(hidden)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));
        assertThat(
                count("select count(*) from blog_post_saves where post_id in (:a, :b)", "a", hidden, "b", blockedOut))
                        .isZero();
    }

    @Test
    void aSaveCanBeTakenBackAfterThePostWasHiddenAndTheAuthorBlockedTheReader() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        UUID post = insertPost(author, marker + " saved then gone");
        save(post, reader, Instant.now());
        setStatus(post, "HIDDEN");
        block(author, reader);

        SignedIn.as(jdbc, reader);
        assertThat(saveService.unsave(post).saved()).isFalse();

        assertThat(count("select count(*) from blog_post_saves where post_id = :p", "p", post)).isZero();
    }

    @Test
    void theSavedListHoldsOnlyTheCallersSavesLatestSaveFirstAndLeavesOutWhatIsNoLongerReadable() {
        UUID author = fixture.learner();
        UUID blockedAuthor = fixture.learner();
        UUID reader = fixture.learner();
        UUID other = fixture.learner();
        Instant base = Instant.now().minus(5, ChronoUnit.DAYS);
        // Written long ago but saved last, and the other way round: the order is the order of the saves.
        UUID savedLast = insertPost(author, marker + " written first", "PUBLISHED", base.minusSeconds(900));
        UUID savedFirst = insertPost(author, marker + " written last", "PUBLISHED", base);
        UUID hidden = insertPost(author, marker + " hidden", "PUBLISHED", base);
        UUID byBlocked = insertPost(blockedAuthor, marker + " by blocked", "PUBLISHED", base);
        UUID notMine = insertPost(author, marker + " saved by someone else", "PUBLISHED", base);
        save(savedFirst, reader, base.plusSeconds(10));
        save(hidden, reader, base.plusSeconds(20));
        save(byBlocked, reader, base.plusSeconds(30));
        save(savedLast, reader, base.plusSeconds(40));
        save(notMine, other, base.plusSeconds(50));
        setStatus(hidden, "HIDDEN");
        block(reader, blockedAuthor);

        assertThat(savedIds(reader)).containsExactly(savedLast, savedFirst);
        SignedIn.as(jdbc, reader);
        var page = saveService.listSaved(PageRequest.of(0, 50));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).allMatch(BlogPostSummaryResult::savedByMe);
        assertThat(page.getContent().get(0).author()).isNotNull();
        assertThat(savedIds(other)).containsExactly(notMine);

        // Hidden and restored: the save never went, so the post is back in the list where its save puts it.
        setStatus(hidden, "PUBLISHED");
        assertThat(savedIds(reader)).containsExactly(savedLast, hidden, savedFirst);
    }

    @Test
    void theSavedListPagesInTheOrderOfTheSavesAndCountsAcrossPages() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        Instant base = Instant.now().minus(1, ChronoUnit.DAYS);
        List<UUID> saved = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID post = insertPost(author, marker + " post " + i);
            save(post, reader, base.plusSeconds(i));
            saved.add(0, post);
        }

        SignedIn.as(jdbc, reader);
        var firstPage = saveService.listSaved(PageRequest.of(0, 2));
        var lastPage = saveService.listSaved(PageRequest.of(2, 2));

        assertThat(firstPage.getContent()).extracting(BlogPostSummaryResult::id).containsExactly(saved.get(0),
                saved.get(1));
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(lastPage.getContent()).extracting(BlogPostSummaryResult::id).containsExactly(saved.get(4));
    }

    @Test
    void aSaveGoesWhenItsPostIsDeletedAndWhenItsReadersAccountIsErased() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        UUID eraser = fixture.learner();
        UUID deleted = insertPost(author, marker + " to delete");
        UUID kept = insertPost(author, marker + " kept");
        save(deleted, reader, Instant.now());
        save(kept, reader, Instant.now());
        save(kept, eraser, Instant.now());

        SignedIn.as(jdbc, author);
        postService.delete(deleted);
        jdbc.sql("delete from users where id = :id").param("id", eraser).update();

        assertThat(count("select count(*) from blog_post_saves where post_id = :p", "p", deleted)).isZero();
        assertThat(count("select count(*) from blog_post_saves where post_id = :p", "p", kept)).isEqualTo(1);
        assertThat(savedIds(reader)).containsExactly(kept);
    }

    // --- recommendations -------------------------------------------------------------------------------------------

    @Test
    void aLearnerIsRecommendedWhatFitsTheirProfileAndNotTheirOwnSavedHiddenBlockedOldOrWeaklyMatchingPosts() {
        UUID author = fixture.learner();
        UUID blockedAuthor = fixture.learner();
        UUID reader = fixture.learner();
        declare(reader, "B1", "IELTS", "SPEAKING");
        Instant recent = Instant.now().minus(3, ChronoUnit.DAYS);

        UUID fits = insertPost(author, marker + " fits", "PUBLISHED", recent);
        UUID alsoFits = insertPost(author, marker + " also fits", "PUBLISHED", recent.minusSeconds(60));
        UUID own = insertPost(reader, marker + " own", "PUBLISHED", recent);
        UUID saved = insertPost(author, marker + " saved", "PUBLISHED", recent);
        UUID hidden = insertPost(author, marker + " hidden", "HIDDEN", recent);
        UUID byBlocked = insertPost(blockedAuthor, marker + " by blocked", "PUBLISHED", recent);
        UUID old = insertPost(author, marker + " old", "PUBLISHED", Instant.now().minus(100, ChronoUnit.DAYS));
        UUID weak = insertPost(author, marker + " weak", "PUBLISHED", recent);
        for (UUID post : List.of(fits, alsoFits, own, saved, hidden, byBlocked, old)) {
            tag(post, "CERTIFICATE", "IELTS");
            tag(post, "SKILL", "SPEAKING");
            tag(post, "LEVEL", "B1");
        }
        // The level next to the reader's own is worth one point, and one point is not enough.
        tag(weak, "LEVEL", "B2");
        save(saved, reader, Instant.now());
        block(reader, blockedAuthor);

        SignedIn.as(jdbc, reader);
        List<BlogRecommendationResult> results = recommendationService.recommend(10);

        assertThat(results).extracting(result -> result.post().id()).contains(fits, alsoFits).doesNotContain(own, saved,
                hidden, byBlocked, old, weak);
        BlogRecommendationResult first = results.stream().filter(result -> result.post().id().equals(fits)).findFirst()
                .orElseThrow();
        assertThat(first.kind()).isEqualTo(BlogRecommendationKind.PROFILE);
        assertThat(first.matchedTags()).extracting(tag -> tag.kind() + ":" + tag.value())
                .containsExactly("SKILL:SPEAKING", "LEVEL:B1", "CERTIFICATE:IELTS");
        assertThat(first.post().author()).isNotNull();
    }

    @Test
    void aPostSavedAfterItWasRecommendedLeavesTheRecommendations() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        declare(reader, "B1", "IELTS", "SPEAKING");
        UUID fits = insertPost(author, marker + " fits", "PUBLISHED", Instant.now().minus(1, ChronoUnit.DAYS));
        tag(fits, "CERTIFICATE", "IELTS");
        tag(fits, "SKILL", "SPEAKING");
        tag(fits, "LEVEL", "B1");
        assertThat(recommendedIds(reader)).contains(fits);

        SignedIn.as(jdbc, reader);
        saveService.save(fits);

        assertThat(recommendedIds(reader)).doesNotContain(fits);
    }

    @Test
    void whoeverHasNoProfileIsRecommendedThePostsMostLikedInTheLastWeek() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Reviewer");
        UUID fan = fixture.learner();
        UUID anotherFan = fixture.learner();
        UUID popular = insertPost(author, marker + " popular", "PUBLISHED", Instant.now().minus(200, ChronoUnit.DAYS));
        UUID likedLongAgo = insertPost(author, marker + " liked long ago");
        UUID unliked = insertPost(author, marker + " unliked");
        UUID hiddenButLiked = insertPost(author, marker + " hidden", "HIDDEN", Instant.now());
        // The week counts the day a like was given, not the day the post was written.
        for (UUID liker : List.of(fan, anotherFan, fixture.learner(), fixture.learner(), fixture.learner())) {
            likeAt(popular, liker, Instant.now().minus(1, ChronoUnit.DAYS));
            likeAt(hiddenButLiked, liker, Instant.now().minus(1, ChronoUnit.DAYS));
            likeAt(likedLongAgo, liker, Instant.now().minus(10, ChronoUnit.DAYS));
        }

        SignedIn.as(jdbc, staff);
        List<BlogRecommendationResult> results = recommendationService.recommend(10);

        assertThat(results).extracting(result -> result.post().id()).contains(popular).doesNotContain(likedLongAgo,
                unliked, hiddenButLiked);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.kind()).isEqualTo(BlogRecommendationKind.POPULAR_THIS_WEEK);
            assertThat(result.matchedTags()).isEmpty();
        });
    }

    @Test
    void thePostsOfAnErasedAuthorCanBeRecommendedToo() {
        UUID reader = fixture.learner();
        declare(reader, "C1", null, "READING");
        UUID orphan = insertPost(fixture.learner(), marker + " orphan", "PUBLISHED", Instant.now());
        tag(orphan, "SKILL", "READING");
        jdbc.sql("update blog_posts set author_id = null where id = :id").param("id", orphan).update();

        assertThat(recommendedIds(reader)).contains(orphan);
    }

    // --- misc ------------------------------------------------------------------------------------------------------

    @Test
    void thePostDetailKnowsItsAuthorTagsAndWhetherTheCallerLikedIt() {
        UUID author = fixture.learner("Author");
        UUID reader = fixture.learner();
        UUID post = insertPost(author, marker + " detailed");
        tag(post, "SKILL", "READING");
        like(post, reader);
        insertComment(post, reader, "visible", "VISIBLE", Instant.now());
        insertComment(post, reader, "hidden", "HIDDEN", Instant.now());

        SignedIn.as(jdbc, reader);
        BlogPostDetailResult detail = postService.get(post);

        assertThat(detail.author().displayName()).isEqualTo("Author");
        assertThat(detail.tags()).singleElement().satisfies(tag -> assertThat(tag.value()).isEqualTo("READING"));
        assertThat(detail.likedByMe()).isTrue();
        assertThat(detail.likeCount()).isEqualTo(1);
        assertThat(detail.commentCount()).isEqualTo(1);
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.canInteract()).isTrue();
        assertThat(detail.body()).isEqualTo("Body of " + marker + " detailed");
    }
}
