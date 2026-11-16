package com.englow3.blog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.blog.dto.command.ListPostsCommand;
import com.englow3.blog.dto.command.ModerationActionCommand;
import com.englow3.blog.dto.command.ModerationQueueFilter;
import com.englow3.blog.dto.command.ReportContentCommand;
import com.englow3.blog.dto.result.BlogPostDetailResult;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.ModerationActionResult;
import com.englow3.blog.dto.result.ModerationQueueItemResult;
import com.englow3.blog.dto.result.ModerationTargetResult;
import com.englow3.blog.dto.result.ReportOutcome;
import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogQueueSort;
import com.englow3.blog.entity.BlogReport;
import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.repository.BlogReportRepository;
import com.englow3.blog.service.BlogCommentService;
import com.englow3.blog.service.BlogModerationService;
import com.englow3.blog.service.BlogPostService;
import com.englow3.blog.service.BlogReportService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.persistence.SqlTime;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * Reports and moderation against real rows: the partial unique index that allows one open report per reader and target,
 * the bulk close of every open report of a piece of content, the lock the moderator takes, the check constraint, the
 * cascade when a reporter's account is erased and the history that outlives it. Rows are made with SQL where the test
 * needs a state the services cannot reach, and every test uses accounts of its own, so tests sharing one database never
 * see each other's content; the queue is read page by page until the test's own entry turns up, because entries left by
 * other tests come first.
 */
class BlogModerationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;
    @Autowired
    BlogReportService reportService;
    @Autowired
    BlogModerationService moderationService;
    @Autowired
    BlogPostService postService;
    @Autowired
    BlogCommentService commentService;
    @Autowired
    BlogReportRepository reportRepo;
    @Autowired
    TransactionTemplate transactions;

    @MockitoBean
    ObjectStorageClient objectStorage;

    private LearnerFixture fixture;
    private String marker;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LearnerFixture(jdbc);
        marker = "zm" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        when(objectStorage.presignGet(anyString(), anyString(), any())).thenAnswer(
                invocation -> java.net.URI.create("https://storage.example/get/" + invocation.getArgument(1)).toURL());
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    private UUID insertPost(UUID author, String title, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into blog_posts (id, author_id, title, body, status, created_at, updated_at)
                values (:id, :author, :title, :body, :status, :at, :at)
                """).param("id", id).param("author", author).param("title", title).param("body", "Body of " + title)
                .param("status", status).param("at", SqlTime.at(Instant.now())).update();
        return id;
    }

    private UUID insertPost(UUID author) {
        return insertPost(author, marker + " a post", "PUBLISHED");
    }

    private UUID insertComment(UUID post, UUID author, String body, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into blog_comments (id, post_id, author_id, body, status, created_at)
                values (:id, :post, :author, :body, :status, :at)
                """).param("id", id).param("post", post).param("author", author).param("body", body)
                .param("status", status).param("at", SqlTime.at(Instant.now())).update();
        return id;
    }

    private void insertReport(UUID reporter, BlogTargetType type, UUID target, String status) {
        jdbc.sql("""
                insert into blog_reports (id, reporter_id, target_type, target_id, reason, status, resolved_at)
                values (:id, :reporter, :type, :target, 'SPAM', :status, :resolvedAt)
                """).param("id", UUID.randomUUID()).param("reporter", reporter).param("type", type.name())
                .param("target", target).param("status", status)
                .param("resolvedAt", "OPEN".equals(status) ? null : SqlTime.at(Instant.now())).update();
    }

    private void block(UUID blocker, UUID blocked) {
        jdbc.sql("insert into user_blocks (blocker_id, blocked_id) values (:a, :b)").param("a", blocker)
                .param("b", blocked).update();
    }

    private long count(String sql, Object... namedParams) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        for (int i = 0; i < namedParams.length; i += 2) {
            spec = spec.param((String) namedParams[i], namedParams[i + 1]);
        }
        return spec.query(Long.class).single();
    }

    private String statusOf(String table, UUID id) {
        return jdbc.sql("select status from " + table + " where id = :id").param("id", id).query(String.class).single();
    }

    private ReportOutcome reportPost(UUID as, UUID post, BlogReportReason reason, String detail) {
        SignedIn.as(jdbc, as);
        return reportService.reportPost(post, new ReportContentCommand(reason, detail));
    }

    private ReportOutcome reportPost(UUID as, UUID post) {
        return reportPost(as, post, BlogReportReason.SPAM, null);
    }

    private ReportOutcome reportComment(UUID as, UUID comment) {
        SignedIn.as(jdbc, as);
        return reportService.reportComment(comment, new ReportContentCommand(BlogReportReason.HARASSMENT, null));
    }

    private ModerationTargetResult act(UUID moderator, BlogTargetType type, UUID target,
            BlogModerationActionType action, String reason) {
        SignedIn.as(jdbc, moderator);
        return moderationService.act(type, target, new ModerationActionCommand(action, reason));
    }

    private ModerationTargetResult actOnPost(UUID moderator, UUID post, BlogModerationActionType action) {
        return act(moderator, BlogTargetType.POST, post, action, "Because " + action);
    }

    /** The queue page by page until the content turns up, since entries of other tests come first. */
    private Optional<ModerationQueueItemResult> queueEntry(UUID moderator, BlogTargetType typeFilter,
            BlogQueueSort sort, BlogTargetType type, UUID id) {
        SignedIn.as(jdbc, moderator);
        for (int page = 0;; page++) {
            var result = moderationService.queue(new ModerationQueueFilter(typeFilter, sort), PageRequest.of(page, 50));
            Optional<ModerationQueueItemResult> found = result.getContent().stream()
                    .filter(item -> item.targetType() == type && item.targetId().equals(id)).findFirst();
            if (found.isPresent() || !result.hasNext()) {
                return found;
            }
        }
    }

    private Optional<ModerationQueueItemResult> queueEntry(UUID moderator, BlogTargetType type, UUID id) {
        return queueEntry(moderator, null, null, type, id);
    }

    private List<UUID> learnersPosts(UUID viewer) {
        SignedIn.as(jdbc, viewer);
        return postService.list(new ListPostsCommand(marker, null, null, null, null, null), PageRequest.of(0, 50))
                .getContent().stream().map(BlogPostSummaryResult::id).toList();
    }

    // --- reporting -------------------------------------------------------------------------------------------------

    @Test
    void threeReadersReportOnePostAndOneOfThemTwiceAndTheQueueHasOneEntryOfThree() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(author);
        UUID first = fixture.learner();
        UUID second = fixture.learner();
        UUID third = fixture.learner();

        ReportOutcome one = reportPost(first, post, BlogReportReason.SPAM, "an advert");
        ReportOutcome sameReaderAgain = reportPost(first, post, BlogReportReason.HARASSMENT, "changed my mind");
        reportPost(second, post, BlogReportReason.OTHER, null);
        reportPost(third, post, BlogReportReason.SPAM, null);

        assertThat(one.created()).isTrue();
        assertThat(sameReaderAgain.created()).isFalse();
        // The first report comes back, as it was filed: the second attempt changes nothing.
        assertThat(sameReaderAgain.report().id()).isEqualTo(one.report().id());
        assertThat(sameReaderAgain.report().reason()).isEqualTo(BlogReportReason.SPAM);
        assertThat(sameReaderAgain.report().detail()).isEqualTo("an advert");
        assertThat(count("select count(*) from blog_reports where target_id = :t", "t", post)).isEqualTo(3);
        ModerationQueueItemResult entry = queueEntry(staff, BlogTargetType.POST, post).orElseThrow();
        assertThat(entry.openReports()).isEqualTo(3);
        assertThat(entry.reasons()).containsExactlyInAnyOrder(BlogReportReason.SPAM, BlogReportReason.OTHER);
        assertThat(entry.title()).isEqualTo(marker + " a post");
        assertThat(entry.excerpt()).isEqualTo("Body of " + marker + " a post");
        assertThat(entry.targetStatus()).isEqualTo("PUBLISHED");
        assertThat(entry.author().userId()).isEqualTo(author);
        assertThat(entry.firstReportedAt()).isBeforeOrEqualTo(entry.lastReportedAt());
    }

    @Test
    void aReaderCannotReportTheirOwnPostOrCommentOrAPostTheyCannotRead() {
        UUID author = fixture.learner();
        UUID reader = fixture.learner();
        UUID blocked = fixture.learner();
        UUID post = insertPost(author);
        UUID hidden = insertPost(author, marker + " hidden", "HIDDEN");
        UUID ownComment = insertComment(post, reader, "mine", "VISIBLE");
        block(author, blocked);

        assertThatThrownBy(() -> reportPost(author, post)).isInstanceOfSatisfying(BadRequestException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_OWN_CONTENT"));
        assertThatThrownBy(() -> reportComment(reader, ownComment)).isInstanceOfSatisfying(BadRequestException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_OWN_CONTENT"));
        assertThatThrownBy(() -> reportPost(reader, hidden)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        assertThatThrownBy(() -> reportPost(blocked, post)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        assertThatThrownBy(() -> reportPost(reader, UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        assertThat(count("select count(*) from blog_reports where target_id in (:a, :b, :c)", "a", post, "b", hidden,
                "c", ownComment)).isZero();
    }

    @Test
    void theAuthorOfAHiddenPostCannotReportItEither() {
        UUID author = fixture.learner();
        UUID hidden = insertPost(author, marker + " hidden", "HIDDEN");

        // The author can read it, so the refusal is the one about their own content, which comes first.
        assertThatThrownBy(() -> reportPost(author, hidden)).isInstanceOfSatisfying(BadRequestException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_OWN_CONTENT"));
    }

    @Test
    void aCommentIsReportedOnlyWhileVisibleOnAPostTheReaderCanReadByAnAuthorTheyHaveNoBlockWith() {
        UUID postAuthor = fixture.learner();
        UUID commenter = fixture.learner();
        UUID reader = fixture.learner();
        UUID blockedFromCommenter = fixture.learner();
        UUID post = insertPost(postAuthor);
        UUID visible = insertComment(post, commenter, "rude words", "VISIBLE");
        UUID hiddenComment = insertComment(post, commenter, "already hidden", "HIDDEN");
        UUID hiddenPost = insertPost(postAuthor, marker + " hidden", "HIDDEN");
        UUID onHiddenPost = insertComment(hiddenPost, commenter, "on a hidden post", "VISIBLE");
        block(commenter, blockedFromCommenter);

        ReportOutcome reported = reportComment(reader, visible);

        assertThat(reported.created()).isTrue();
        assertThat(reported.report().targetType()).isEqualTo(BlogTargetType.COMMENT);
        assertThat(reported.report().targetId()).isEqualTo(visible);
        assertThat(reportComment(reader, visible).created()).isFalse();
        for (UUID unreadable : List.of(hiddenComment, onHiddenPost, UUID.randomUUID())) {
            assertThatThrownBy(() -> reportComment(reader, unreadable)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));
        }
        assertThatThrownBy(() -> reportComment(blockedFromCommenter, visible)).isInstanceOfSatisfying(
                NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));
    }

    @Test
    void aReportNoteOverFiveHundredCharactersIsRefusedAndNothingIsFiled() {
        UUID post = insertPost(fixture.learner());
        UUID reader = fixture.learner();

        assertThatThrownBy(() -> reportPost(reader, post, BlogReportReason.OTHER, "x".repeat(501)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_DETAIL_TOO_LONG"));

        assertThat(count("select count(*) from blog_reports where target_id = :t", "t", post)).isZero();
    }

    // --- hiding, restoring, removing -------------------------------------------------------------------------------

    @Test
    void hidingClosesEveryOpenReportAsResolvedByTheSameModeratorAndRecordsOneAction() {
        UUID author = fixture.learner("Writer");
        UUID staff = fixture.staff("Mod");
        UUID viewer = fixture.learner();
        UUID post = insertPost(author);
        reportPost(fixture.learner(), post);
        reportPost(fixture.learner(), post);
        reportPost(fixture.learner(), post);
        assertThat(learnersPosts(viewer)).contains(post);

        ModerationTargetResult result = act(staff, BlogTargetType.POST, post, BlogModerationActionType.HIDE,
                "  Advertising  ");

        assertThat(statusOf("blog_posts", post)).isEqualTo("HIDDEN");
        assertThat(count("select count(*) from blog_reports where target_id = :t and status = 'RESOLVED'"
                + " and resolved_by_user_id = :m and resolution_note = 'Advertising' and resolved_at is not null", "t",
                post, "m", staff)).isEqualTo(3);
        assertThat(count("select count(*) from blog_reports where target_id = :t and status = 'OPEN'", "t", post))
                .isZero();
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t", "t", post)).isEqualTo(1);
        assertThat(result.targetStatus()).isEqualTo("HIDDEN");
        assertThat(result.reports()).hasSize(3)
                .allSatisfy(report -> assertThat(report.status()).isEqualTo(BlogReportStatus.RESOLVED));
        assertThat(result.actions()).singleElement().satisfies(action -> {
            assertThat(action.action()).isEqualTo(BlogModerationActionType.HIDE);
            assertThat(action.reason()).isEqualTo("Advertising");
            assertThat(action.moderator().displayName()).isEqualTo("Mod");
        });
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t and report_id is not null",
                "t", post)).isEqualTo(1);
        assertThat(learnersPosts(viewer)).doesNotContain(post);
        assertThat(queueEntry(staff, BlogTargetType.POST, post)).isEmpty();
    }

    @Test
    void theActionPointsAtTheMostRecentOpenReport() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());
        reportPost(fixture.learner(), post);
        UUID latestReporter = fixture.learner();
        reportPost(latestReporter, post);
        UUID latest = jdbc.sql("select id from blog_reports where target_id = :t and reporter_id = :r").param("t", post)
                .param("r", latestReporter).query(UUID.class).single();
        // Made a moment later than the first, for certain: created_at has the clock's resolution.
        jdbc.sql("update blog_reports set created_at = created_at + interval '1 minute' where id = :id")
                .param("id", latest).update();

        actOnPost(staff, post, BlogModerationActionType.HIDE);

        assertThat(jdbc.sql("select report_id from blog_moderation_actions where target_id = :t").param("t", post)
                .query(UUID.class).single()).isEqualTo(latest);
    }

    @Test
    void aModeratorCanActOnContentNobodyReported() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());

        ModerationTargetResult result = actOnPost(staff, post, BlogModerationActionType.HIDE);

        assertThat(result.targetStatus()).isEqualTo("HIDDEN");
        assertThat(jdbc.sql("select report_id from blog_moderation_actions where target_id = :t").param("t", post)
                .query((rs, row) -> rs.getObject("report_id", UUID.class)).list()).containsExactly((UUID) null);
    }

    @Test
    void theAuthorOfAHiddenPostSeesTheReasonAndOnlyTheAuthorDoes() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(author);
        act(staff, BlogTargetType.POST, post, BlogModerationActionType.HIDE, "Advertising");

        SignedIn.as(jdbc, author);
        BlogPostDetailResult detail = postService.get(post);

        assertThat(detail.status().name()).isEqualTo("HIDDEN");
        assertThat(detail.moderationNote()).isEqualTo("Advertising");
        assertThat(detail.canEdit()).isFalse();
        assertThat(detail.canInteract()).isFalse();
        // Nobody else is given the post at all, so nobody else is given a note.
        SignedIn.as(jdbc, fixture.learner());
        assertThatThrownBy(() -> postService.get(post)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theNoteStaysTheReasonOfTheLastHideOrRemovalWhateverIsRecordedAfterIt() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID otherStaff = fixture.staff("Other mod");
        UUID post = insertPost(author);
        act(staff, BlogTargetType.POST, post, BlogModerationActionType.HIDE, "Advertising");
        // A report that reached the post another way (a script, an old client), dismissed after the hide.
        insertReport(fixture.learner(), BlogTargetType.POST, post, "OPEN");
        act(otherStaff, BlogTargetType.POST, post, BlogModerationActionType.DISMISS, "Fine by me");

        SignedIn.as(jdbc, author);
        assertThat(postService.get(post).moderationNote()).isEqualTo("Advertising");

        act(otherStaff, BlogTargetType.POST, post, BlogModerationActionType.REMOVE, "Worse than I thought");
        SignedIn.as(jdbc, author);
        assertThat(postService.get(post).moderationNote()).isEqualTo("Worse than I thought");
    }

    @Test
    void aPublishedPostHasNoNote() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(author);
        act(staff, BlogTargetType.POST, post, BlogModerationActionType.HIDE, "Advertising");
        act(fixture.staff("Other mod"), BlogTargetType.POST, post, BlogModerationActionType.RESTORE, "My mistake");

        SignedIn.as(jdbc, author);
        BlogPostDetailResult detail = postService.get(post);

        assertThat(detail.status().name()).isEqualTo("PUBLISHED");
        assertThat(detail.moderationNote()).isNull();
    }

    @Test
    void restoringBringsAHiddenPostBackAndKeepsTheHistory() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID viewer = fixture.learner();
        UUID post = insertPost(author);
        reportPost(fixture.learner(), post);
        actOnPost(staff, post, BlogModerationActionType.HIDE);

        ModerationTargetResult result = actOnPost(staff, post, BlogModerationActionType.RESTORE);

        assertThat(statusOf("blog_posts", post)).isEqualTo("PUBLISHED");
        assertThat(learnersPosts(viewer)).contains(post);
        assertThat(result.targetStatus()).isEqualTo("PUBLISHED");
        // Newest first; the report the hide resolved stays resolved.
        assertThat(result.actions()).extracting(ModerationTargetResult.Action::action)
                .containsExactly(BlogModerationActionType.RESTORE, BlogModerationActionType.HIDE);
        assertThat(result.reports()).singleElement()
                .satisfies(report -> assertThat(report.status()).isEqualTo(BlogReportStatus.RESOLVED));
        assertThat(result.content().tags()).isEmpty();
    }

    @Test
    void restoreLeavesTheOpenReportsOfTheContentAsTheyWere() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner(), marker + " hidden", "HIDDEN");
        insertReport(fixture.learner(), BlogTargetType.POST, post, "OPEN");

        actOnPost(staff, post, BlogModerationActionType.RESTORE);

        assertThat(count("select count(*) from blog_reports where target_id = :t and status = 'OPEN'", "t", post))
                .isEqualTo(1);
    }

    @Test
    void aRemovedPostCannotBeRestoredOrHiddenOrRemovedAgainAndNothingIsRecordedForTheRefusal() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());
        actOnPost(staff, post, BlogModerationActionType.REMOVE);

        for (BlogModerationActionType action : List.of(BlogModerationActionType.RESTORE, BlogModerationActionType.HIDE,
                BlogModerationActionType.REMOVE)) {
            assertThatThrownBy(() -> actOnPost(staff, post, action)).isInstanceOfSatisfying(ConflictException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_INVALID_TRANSITION"));
        }

        assertThat(statusOf("blog_posts", post)).isEqualTo("REMOVED");
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t", "t", post)).isEqualTo(1);
    }

    @Test
    void aRefusedTransitionLeavesTheOpenReportsOpen() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner(), marker + " hidden", "HIDDEN");
        insertReport(fixture.learner(), BlogTargetType.POST, post, "OPEN");

        assertThatThrownBy(() -> actOnPost(staff, post, BlogModerationActionType.HIDE))
                .isInstanceOf(ConflictException.class);

        assertThat(count("select count(*) from blog_reports where target_id = :t and status = 'OPEN'", "t", post))
                .isEqualTo(1);
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t", "t", post)).isZero();
    }

    @Test
    void hidingAndRemovingAreRecordedForCommentsToo() {
        UUID postAuthor = fixture.learner();
        UUID commenter = fixture.learner();
        UUID reader = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(postAuthor);
        UUID comment = insertComment(post, commenter, "rude words", "VISIBLE");
        reportComment(reader, comment);

        ModerationTargetResult hidden = act(staff, BlogTargetType.COMMENT, comment, BlogModerationActionType.HIDE,
                "Rude");

        assertThat(statusOf("blog_comments", comment)).isEqualTo("HIDDEN");
        assertThat(hidden.targetStatus()).isEqualTo("HIDDEN");
        assertThat(hidden.content().postId()).isEqualTo(post);
        assertThat(hidden.content().title()).isEqualTo(marker + " a post");
        assertThat(hidden.content().body()).isEqualTo("rude words");
        assertThat(
                count("select count(*) from blog_reports where target_id = :t and status = 'RESOLVED'", "t", comment))
                        .isEqualTo(1);
        // Gone for the readers; the commenter sees their own, hidden.
        SignedIn.as(jdbc, reader);
        assertThat(commentService.list(post, PageRequest.of(0, 20)).getContent()).isEmpty();
        SignedIn.as(jdbc, commenter);
        assertThat(commentService.list(post, PageRequest.of(0, 20)).getContent())
                .extracting(shown -> shown.status().name()).containsExactly("HIDDEN");

        act(staff, BlogTargetType.COMMENT, comment, BlogModerationActionType.REMOVE, "Worse");
        assertThat(statusOf("blog_comments", comment)).isEqualTo("REMOVED");
        assertThatThrownBy(() -> act(staff, BlogTargetType.COMMENT, comment, BlogModerationActionType.RESTORE, "No"))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_INVALID_TRANSITION"));
    }

    // --- dismissing ------------------------------------------------------------------------------------------------

    @Test
    void dismissingClosesTheReportsAsDismissedLeavesThePostPublishedAndRecordsOneAction() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(author);
        reportPost(fixture.learner(), post);
        reportPost(fixture.learner(), post);

        ModerationTargetResult result = act(staff, BlogTargetType.POST, post, BlogModerationActionType.DISMISS,
                "Nothing wrong");

        assertThat(statusOf("blog_posts", post)).isEqualTo("PUBLISHED");
        assertThat(count("select count(*) from blog_reports where target_id = :t and status = 'DISMISSED'"
                + " and resolved_by_user_id = :m and resolution_note = 'Nothing wrong'", "t", post, "m", staff))
                        .isEqualTo(2);
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t and action = 'DISMISS'",
                "t", post)).isEqualTo(1);
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t and report_id is not null",
                "t", post)).isEqualTo(1);
        assertThat(result.targetStatus()).isEqualTo("PUBLISHED");
        assertThat(result.actions()).singleElement()
                .satisfies(action -> assertThat(action.action()).isEqualTo(BlogModerationActionType.DISMISS));
        assertThat(queueEntry(staff, BlogTargetType.POST, post)).isEmpty();
    }

    @Test
    void dismissingWithNothingOpenIsRefusedAndRecordsNothing() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());

        assertThatThrownBy(() -> actOnPost(staff, post, BlogModerationActionType.DISMISS)).isInstanceOfSatisfying(
                ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_NO_OPEN_REPORTS"));

        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t", "t", post)).isZero();
    }

    @Test
    void aReaderWhoseReportWasDismissedMayReportAgainAndAThirdWhileTheSecondIsOpenIsTheSecond() {
        UUID staff = fixture.staff("Mod");
        UUID reader = fixture.learner();
        UUID post = insertPost(fixture.learner());
        ReportOutcome first = reportPost(reader, post);
        actOnPost(staff, post, BlogModerationActionType.DISMISS);
        assertThat(queueEntry(staff, BlogTargetType.POST, post)).isEmpty();

        // The content may have been edited into something worth reporting since.
        ReportOutcome second = reportPost(reader, post, BlogReportReason.MISINFORMATION, "now it lies");
        ReportOutcome third = reportPost(reader, post);

        assertThat(second.created()).isTrue();
        assertThat(second.report().id()).isNotEqualTo(first.report().id());
        assertThat(second.report().status()).isEqualTo(BlogReportStatus.OPEN);
        assertThat(third.created()).isFalse();
        assertThat(third.report().id()).isEqualTo(second.report().id());
        assertThat(count("select count(*) from blog_reports where target_id = :t and reporter_id = :r", "t", post, "r",
                reader)).isEqualTo(2);
        assertThat(queueEntry(staff, BlogTargetType.POST, post))
                .hasValueSatisfying(entry -> assertThat(entry.openReports()).isEqualTo(1));
    }

    @Test
    void aReportOfAHiddenThenRestoredPostCanBeFiledAgainToo() {
        UUID staff = fixture.staff("Mod");
        UUID reader = fixture.learner();
        UUID post = insertPost(fixture.learner());
        reportPost(reader, post);
        actOnPost(staff, post, BlogModerationActionType.HIDE);
        actOnPost(staff, post, BlogModerationActionType.RESTORE);

        assertThat(reportPost(reader, post).created()).isTrue();
    }

    // --- the moderator's own content -------------------------------------------------------------------------------

    @Test
    void aModeratorNeverActsOnTheirOwnPostOrCommentWhateverTheActionAndAnotherModeratorDoes() {
        UUID staff = fixture.staff("Writer and mod");
        UUID otherStaff = fixture.staff("Other mod");
        UUID reader = fixture.learner();
        UUID post = insertPost(staff);
        UUID comment = insertComment(insertPost(fixture.learner()), staff, "my comment", "VISIBLE");
        reportPost(reader, post);
        reportComment(reader, comment);

        for (BlogModerationActionType action : BlogModerationActionType.values()) {
            assertThatThrownBy(() -> actOnPost(staff, post, action)).isInstanceOfSatisfying(ForbiddenException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_OWN_CONTENT"));
            assertThatThrownBy(() -> act(staff, BlogTargetType.COMMENT, comment, action, "Mine"))
                    .isInstanceOf(ForbiddenException.class);
        }
        assertThat(statusOf("blog_posts", post)).isEqualTo("PUBLISHED");
        assertThat(count("select count(*) from blog_reports where target_id in (:p, :c) and status = 'OPEN'", "p", post,
                "c", comment)).isEqualTo(2);
        assertThat(count("select count(*) from blog_moderation_actions where target_id in (:p, :c)", "p", post, "c",
                comment)).isZero();

        actOnPost(otherStaff, post, BlogModerationActionType.HIDE);
        assertThat(statusOf("blog_posts", post)).isEqualTo("HIDDEN");
    }

    @Test
    void aModeratorMayStillReadTheirOwnContentInTheModerationView() {
        UUID staff = fixture.staff("Writer and mod");
        UUID post = insertPost(staff);

        SignedIn.as(jdbc, staff);

        assertThat(moderationService.target(BlogTargetType.POST, post).content().title()).isEqualTo(marker + " a post");
    }

    // --- content that is gone --------------------------------------------------------------------------------------

    @Test
    void aPostItsAuthorDeletedStaysInTheQueueAsDeletedUntilSomeoneDismissesItsReports() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(author);
        reportPost(fixture.learner(), post);
        reportPost(fixture.learner(), post);
        SignedIn.as(jdbc, author);
        postService.delete(post);

        ModerationQueueItemResult entry = queueEntry(staff, BlogTargetType.POST, post).orElseThrow();

        assertThat(entry.targetStatus()).isEqualTo("DELETED");
        assertThat(entry.openReports()).isEqualTo(2);
        assertThat(entry.title()).isNull();
        assertThat(entry.excerpt()).isNull();
        assertThat(entry.author()).isNull();
        SignedIn.as(jdbc, staff);
        ModerationTargetResult target = moderationService.target(BlogTargetType.POST, post);
        assertThat(target.targetStatus()).isEqualTo("DELETED");
        assertThat(target.content()).isNull();
        assertThat(target.author()).isNull();
        assertThat(target.reports()).hasSize(2);

        ModerationTargetResult cleaned = act(staff, BlogTargetType.POST, post, BlogModerationActionType.DISMISS,
                "Gone already");

        assertThat(queueEntry(staff, BlogTargetType.POST, post)).isEmpty();
        assertThat(cleaned.targetStatus()).isEqualTo("DELETED");
        assertThat(cleaned.actions()).hasSize(1);
        // Hiding something that is not there is not found; the history has something to show, so the view is not.
        assertThatThrownBy(() -> act(staff, BlogTargetType.POST, post, BlogModerationActionType.HIDE, "Too late"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_TARGET_NOT_FOUND"));
    }

    @Test
    void aCommentItsAuthorDeletedStaysInTheQueueAsDeleted() {
        UUID commenter = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());
        UUID comment = insertComment(post, commenter, "rude words", "VISIBLE");
        reportComment(fixture.learner(), comment);
        SignedIn.as(jdbc, commenter);
        commentService.delete(comment);

        ModerationQueueItemResult entry = queueEntry(staff, BlogTargetType.COMMENT, comment).orElseThrow();

        assertThat(entry.targetStatus()).isEqualTo("DELETED");
        assertThat(entry.title()).isNull();
    }

    @Test
    void contentWithNoReportNoHistoryAndNoRowIsNotFound() {
        SignedIn.as(jdbc, fixture.staff("Mod"));

        assertThatThrownBy(() -> moderationService.target(BlogTargetType.POST, UUID.randomUUID()))
                .isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_TARGET_NOT_FOUND"));
        assertThatThrownBy(() -> moderationService.target(BlogTargetType.COMMENT, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> act(fixture.staff("Mod"), BlogTargetType.POST, UUID.randomUUID(),
                BlogModerationActionType.HIDE, "Nothing there")).isInstanceOfSatisfying(NotFoundException.class,
                        e -> assertThat(e.getCode()).isEqualTo("BLOG_TARGET_NOT_FOUND"));
    }

    // --- the queue and the views -----------------------------------------------------------------------------------

    @Test
    void theQueueOrdersByOldestOrMostReportedAndFiltersByType() {
        UUID staff = fixture.staff("Mod");
        UUID older = insertPost(fixture.learner(), marker + " older", "PUBLISHED");
        UUID busy = insertPost(fixture.learner(), marker + " busy", "PUBLISHED");
        UUID comment = insertComment(insertPost(fixture.learner()), fixture.learner(), "a comment " + marker,
                "VISIBLE");
        reportPost(fixture.learner(), older);
        reportPost(fixture.learner(), busy);
        reportPost(fixture.learner(), busy);
        reportPost(fixture.learner(), busy);
        reportComment(fixture.learner(), comment);
        // Reported earlier than anything else of this test, so "oldest" has a certain answer.
        jdbc.sql("update blog_reports set created_at = created_at - interval '1 hour' where target_id = :t")
                .param("t", older).update();

        List<UUID> oldestFirst = mineInOrder(staff, null, BlogQueueSort.OLDEST, older, busy, comment);
        List<UUID> mostReportedFirst = mineInOrder(staff, null, BlogQueueSort.MOST_REPORTED, older, busy, comment);
        List<UUID> postsOnly = mineInOrder(staff, BlogTargetType.POST, BlogQueueSort.OLDEST, older, busy, comment);
        List<UUID> commentsOnly = mineInOrder(staff, BlogTargetType.COMMENT, BlogQueueSort.OLDEST, older, busy,
                comment);

        assertThat(oldestFirst).startsWith(older);
        assertThat(mostReportedFirst).as("three reports, then the older of the two with one").containsExactly(busy,
                older, comment);
        assertThat(postsOnly).containsExactly(older, busy);
        assertThat(commentsOnly).containsExactly(comment);
        assertThat(
                queueEntry(staff, null, BlogQueueSort.OLDEST, BlogTargetType.COMMENT, comment).orElseThrow().excerpt())
                        .isEqualTo("a comment " + marker);
    }

    /** The ids among these that the queue lists, in the order it lists them, whatever else it lists around them. */
    private List<UUID> mineInOrder(UUID staff, BlogTargetType typeFilter, BlogQueueSort sort, UUID... mine) {
        SignedIn.as(jdbc, staff);
        List<UUID> mineSet = List.of(mine);
        List<UUID> found = new java.util.ArrayList<>();
        for (int page = 0;; page++) {
            var result = moderationService.queue(new ModerationQueueFilter(typeFilter, sort), PageRequest.of(page, 50));
            result.getContent().stream().map(ModerationQueueItemResult::targetId).filter(mineSet::contains)
                    .forEach(found::add);
            if (!result.hasNext()) {
                return found;
            }
        }
    }

    @Test
    void theQueueTotalCountsContentNotReportsAndAPageIsAtMostFifty() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());
        reportPost(fixture.learner(), post);
        reportPost(fixture.learner(), post);

        SignedIn.as(jdbc, staff);
        var onlyPosts = moderationService.queue(new ModerationQueueFilter(BlogTargetType.POST, null),
                PageRequest.of(0, 1));
        var huge = moderationService.queue(new ModerationQueueFilter(null, null), PageRequest.of(0, 5_000));
        var pastTheEnd = moderationService.queue(new ModerationQueueFilter(null, null),
                PageRequest.of(Integer.MAX_VALUE / 2, 50));

        assertThat(onlyPosts.getContent()).hasSize(1);
        assertThat(onlyPosts.getTotalElements()).isGreaterThanOrEqualTo(1);
        assertThat(huge.getSize()).isEqualTo(50);
        assertThat(huge.getContent().size()).isLessThanOrEqualTo(50);
        assertThat(pastTheEnd.getContent()).isEmpty();
    }

    @Test
    void theTargetViewHoldsTheContentEveryReportNewestFirstAndTheHistoryWithNames() {
        UUID author = fixture.learner("Writer");
        UUID staff = fixture.staff("Mod");
        UUID reporterA = fixture.learner("Reporter A");
        UUID reporterB = fixture.learner("Reporter B");
        UUID post = insertPost(author);
        jdbc.sql("insert into blog_post_tags (post_id, kind, tag_value) values (:p, 'SKILL', 'WRITING')")
                .param("p", post).update();
        reportPost(reporterA, post, BlogReportReason.SPAM, "an advert");
        reportPost(reporterB, post, BlogReportReason.HARASSMENT, null);
        jdbc.sql("update blog_reports set created_at = created_at - interval '1 hour' where reporter_id = :r")
                .param("r", reporterA).update();

        SignedIn.as(jdbc, staff);
        ModerationTargetResult target = moderationService.target(BlogTargetType.POST, post);

        assertThat(target.targetType()).isEqualTo(BlogTargetType.POST);
        assertThat(target.targetStatus()).isEqualTo("PUBLISHED");
        assertThat(target.author().displayName()).isEqualTo("Writer");
        assertThat(target.content().title()).isEqualTo(marker + " a post");
        assertThat(target.content().body()).isEqualTo("Body of " + marker + " a post");
        assertThat(target.content().postId()).isNull();
        assertThat(target.content().tags()).singleElement()
                .satisfies(tag -> assertThat(tag.value()).isEqualTo("WRITING"));
        assertThat(target.reports()).extracting(report -> report.reporter().displayName()).containsExactly("Reporter B",
                "Reporter A");
        assertThat(target.reports()).extracting(ModerationTargetResult.Report::reason)
                .containsExactly(BlogReportReason.HARASSMENT, BlogReportReason.SPAM);
        assertThat(target.reports().get(1).detail()).isEqualTo("an advert");
        assertThat(target.reports().get(0).detail()).isNull();
        assertThat(target.reports()).allSatisfy(report -> {
            assertThat(report.status()).isEqualTo(BlogReportStatus.OPEN);
            assertThat(report.resolvedAt()).isNull();
            assertThat(report.resolutionNote()).isNull();
        });
        assertThat(target.actions()).isEmpty();
    }

    @Test
    void theHistoryOfModerationIsNewestFirstWithTheModeratorsName() {
        UUID staff = fixture.staff("History mod");
        UUID first = insertPost(fixture.learner());
        UUID second = insertPost(fixture.learner());
        actOnPost(staff, first, BlogModerationActionType.HIDE);
        actOnPost(staff, second, BlogModerationActionType.REMOVE);

        SignedIn.as(jdbc, staff);
        List<ModerationActionResult> recent = moderationService.recentActions(PageRequest.of(0, 50)).getContent();

        List<ModerationActionResult> mine = recent.stream()
                .filter(action -> action.targetId().equals(first) || action.targetId().equals(second)).toList();
        assertThat(mine).extracting(ModerationActionResult::targetId).containsExactly(second, first);
        assertThat(mine).extracting(ModerationActionResult::action).containsExactly(BlogModerationActionType.REMOVE,
                BlogModerationActionType.HIDE);
        assertThat(mine).allSatisfy(action -> assertThat(action.moderator().displayName()).isEqualTo("History mod"));
        assertThat(moderationService.recentActions(PageRequest.of(0, 5_000)).getSize()).isEqualTo(50);
    }

    // --- erasure ---------------------------------------------------------------------------------------------------

    @Test
    void erasingAReportersAccountDeletesTheirReportsAndKeepsWhatModeratorsDid() {
        UUID leaver = fixture.learner("Leaver");
        UUID stayer = fixture.learner("Stayer");
        UUID staff = fixture.staff("Mod");
        UUID closedPost = insertPost(fixture.learner());
        UUID openPost = insertPost(fixture.learner());
        reportPost(leaver, closedPost);
        reportPost(stayer, closedPost);
        reportPost(leaver, openPost);
        actOnPost(staff, closedPost, BlogModerationActionType.HIDE);

        jdbc.sql("delete from users where id = :id").param("id", leaver).update();

        assertThat(count("select count(*) from blog_reports where reporter_id = :id", "id", leaver)).isZero();
        assertThat(count("select count(*) from blog_reports where target_id = :t", "t", closedPost)).isEqualTo(1);
        assertThat(queueEntry(staff, BlogTargetType.POST, openPost)).isEmpty();
        // The action stays; the report it pointed at may be gone, and then it points at none.
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t", "t", closedPost))
                .isEqualTo(1);
        SignedIn.as(jdbc, staff);
        assertThat(moderationService.target(BlogTargetType.POST, closedPost).actions()).hasSize(1);
    }

    @Test
    void anActionStaysWhenTheReportItClosedIsDeletedWithItsReporter() {
        UUID leaver = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());
        reportPost(leaver, post);
        actOnPost(staff, post, BlogModerationActionType.HIDE);
        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t and report_id is not null",
                "t", post)).isEqualTo(1);

        jdbc.sql("delete from users where id = :id").param("id", leaver).update();

        assertThat(count("select count(*) from blog_moderation_actions where target_id = :t and report_id is null", "t",
                post)).isEqualTo(1);
    }

    @Test
    void anAccountThatClosedReportsCannotBeErasedWhileTheyAreOnRecord() {
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(fixture.learner());
        reportPost(fixture.learner(), post);
        actOnPost(staff, post, BlogModerationActionType.HIDE);

        assertThatThrownBy(() -> jdbc.sql("delete from users where id = :id").param("id", staff).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- the schema's own rules ------------------------------------------------------------------------------------

    @Test
    void theDatabaseAllowsOneOpenReportPerReaderAndTargetButNotTwo() {
        UUID reader = fixture.learner();
        UUID target = UUID.randomUUID();

        insertReport(reader, BlogTargetType.POST, target, "OPEN");

        assertThatThrownBy(() -> insertReport(reader, BlogTargetType.POST, target, "OPEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Another reader, another target and another kind of target are each their own.
        insertReport(fixture.learner(), BlogTargetType.POST, target, "OPEN");
        insertReport(reader, BlogTargetType.POST, UUID.randomUUID(), "OPEN");
        insertReport(reader, BlogTargetType.COMMENT, target, "OPEN");
    }

    @Test
    void closedReportsDoNotBlockANewOpenOneNorEachOther() {
        UUID reader = fixture.learner();
        UUID target = UUID.randomUUID();

        insertReport(reader, BlogTargetType.POST, target, "DISMISSED");
        insertReport(reader, BlogTargetType.POST, target, "RESOLVED");
        insertReport(reader, BlogTargetType.POST, target, "DISMISSED");
        insertReport(reader, BlogTargetType.POST, target, "OPEN");

        assertThat(count("select count(*) from blog_reports where reporter_id = :r", "r", reader)).isEqualTo(4);
    }

    @Test
    void anOpenReportMustHaveNoResolutionTimeAndAClosedOneMustHaveOne() {
        UUID reader = fixture.learner();

        assertThatThrownBy(() -> jdbc.sql("""
                insert into blog_reports (id, reporter_id, target_type, target_id, reason, status)
                values (:id, :r, 'POST', :t, 'SPAM', 'RESOLVED')
                """).param("id", UUID.randomUUID()).param("r", reader).param("t", UUID.randomUUID()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                insert into blog_reports (id, reporter_id, target_type, target_id, reason, status, resolved_at)
                values (:id, :r, 'POST', :t, 'SPAM', 'OPEN', now())
                """).param("id", UUID.randomUUID()).param("r", reader).param("t", UUID.randomUUID()).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesWhatTheEntitiesWouldRefuse() {
        UUID reader = fixture.learner();
        UUID staff = fixture.staff("Mod");

        for (String badRow : new String[] { "'THEFT', 'OPEN'", "'SPAM', 'PENDING'" }) {
            assertThatThrownBy(() -> jdbc
                    .sql("insert into blog_reports (id, reporter_id, target_type, target_id,"
                            + " reason, status) values (:id, :r, 'POST', :t, " + badRow + ")")
                    .param("id", UUID.randomUUID()).param("r", reader).param("t", UUID.randomUUID()).update())
                            .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc
                .sql("insert into blog_reports (id, reporter_id, target_type, target_id, reason,"
                        + " detail) values (:id, :r, 'POST', :t, 'SPAM', :d)")
                .param("id", UUID.randomUUID()).param("r", reader).param("t", UUID.randomUUID())
                .param("d", "x".repeat(501)).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc
                .sql("insert into blog_reports (id, reporter_id, target_type, target_id, reason)"
                        + " values (:id, :r, 'USER', :t, 'SPAM')")
                .param("id", UUID.randomUUID()).param("r", reader).param("t", UUID.randomUUID()).update())
                        .isInstanceOf(DataIntegrityViolationException.class);
        for (String badAction : new String[] { "'DELETE', 'reason'", "'HIDE', '   '", "'HIDE', null" }) {
            assertThatThrownBy(() -> jdbc
                    .sql("insert into blog_moderation_actions (id, target_type, target_id, action,"
                            + " reason, moderator_id) values (:id, 'POST', :t, " + badAction + ", :m)")
                    .param("id", UUID.randomUUID()).param("t", UUID.randomUUID()).param("m", staff).update())
                            .isInstanceOf(DataIntegrityViolationException.class);
        }
        // DISMISS is one of the four.
        jdbc.sql("insert into blog_moderation_actions (id, target_type, target_id, action, reason, moderator_id)"
                + " values (:id, 'POST', :t, 'DISMISS', 'ok', :m)").param("id", UUID.randomUUID())
                .param("t", UUID.randomUUID()).param("m", staff).update();
    }

    // --- the two ways of closing a report --------------------------------------------------------------------------

    /**
     * {@code BlogReport#resolve} keeps the rule for closing one report; the repository's one statement closes all of
     * them. Whatever one writes, the other writes the same.
     */
    @Test
    void closingThroughTheEntityAndThroughTheBulkStatementLeaveTheSameRow() {
        UUID moderator = fixture.staff("Mod");
        UUID viaEntity = UUID.randomUUID();
        UUID viaStatement = UUID.randomUUID();
        UUID reporter = fixture.learner();
        insertReport(reporter, BlogTargetType.POST, viaEntity, "OPEN");
        insertReport(reporter, BlogTargetType.POST, viaStatement, "OPEN");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        transactions.executeWithoutResult(tx -> {
            BlogReport report = reportRepo.findByReporterIdAndTargetTypeAndTargetIdAndStatus(reporter,
                    BlogTargetType.POST, viaEntity, BlogReportStatus.OPEN).orElseThrow();
            report.resolve(moderator, "Hidden", now);
            reportRepo.saveAndFlush(report);
        });
        int closed = transactions.execute(tx -> reportRepo.closeOpen(BlogTargetType.POST, viaStatement,
                BlogReportStatus.RESOLVED, moderator, "Hidden", now));

        assertThat(closed).isEqualTo(1);
        String sql = "select status || '|' || resolved_by_user_id || '|' || resolution_note || '|' || resolved_at"
                + " from blog_reports where target_id = :t";
        assertThat(jdbc.sql(sql).param("t", viaStatement).query(String.class).single())
                .isEqualTo(jdbc.sql(sql).param("t", viaEntity).query(String.class).single());
    }

    @Test
    void theBulkStatementClosesOnlyTheOpenReportsOfThatContent() {
        UUID moderator = fixture.staff("Mod");
        UUID target = UUID.randomUUID();
        UUID otherTarget = UUID.randomUUID();
        insertReport(fixture.learner(), BlogTargetType.POST, target, "OPEN");
        insertReport(fixture.learner(), BlogTargetType.POST, target, "OPEN");
        insertReport(fixture.learner(), BlogTargetType.POST, target, "DISMISSED");
        insertReport(fixture.learner(), BlogTargetType.POST, otherTarget, "OPEN");
        insertReport(fixture.learner(), BlogTargetType.COMMENT, target, "OPEN");

        int closed = transactions.execute(tx -> reportRepo.closeOpen(BlogTargetType.POST, target,
                BlogReportStatus.DISMISSED, moderator, "Fine", Instant.now()));

        assertThat(closed).isEqualTo(2);
        assertThat(count("select count(*) from blog_reports where target_id = :t and target_type = 'POST'"
                + " and status = 'OPEN'", "t", target)).isZero();
        assertThat(
                count("select count(*) from blog_reports where target_id = :t and status = 'OPEN'", "t", otherTarget))
                        .isEqualTo(1);
        assertThat(count("select count(*) from blog_reports where target_id = :t and target_type = 'COMMENT'"
                + " and status = 'OPEN'", "t", target)).isEqualTo(1);
    }

    // --- two moderators at once ------------------------------------------------------------------------------------

    /**
     * What the lock is for: two moderators hide the same post at the same moment. Without it both read PUBLISHED and
     * the second dies of a version check, with an error that says nothing; with it the second waits for the first to
     * commit, reads the post as hidden and is told so. Either way one action is recorded.
     */
    @Test
    void twoModeratorsHidingTheSamePostAtOnceLeaveOneActionAndTheSecondIsToldItIsAlreadyHidden() throws Exception {
        UUID staffA = fixture.staff("Mod A");
        UUID staffB = fixture.staff("Mod B");
        UUID post = insertPost(fixture.learner());
        reportPost(fixture.learner(), post);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<String>> outcomes = new java.util.ArrayList<>();
            for (UUID moderator : List.of(staffA, staffB)) {
                outcomes.add(pool.submit(() -> {
                    SignedIn.as(jdbc, moderator);
                    try {
                        go.await(30, java.util.concurrent.TimeUnit.SECONDS);
                        moderationService.act(BlogTargetType.POST, post,
                                new ModerationActionCommand(BlogModerationActionType.HIDE, "Advertising"));
                        return "hidden";
                    } catch (ConflictException refused) {
                        return refused.getCode();
                    } finally {
                        SignedIn.out();
                    }
                }));
            }
            go.countDown();
            List<String> results = new java.util.ArrayList<>();
            for (var outcome : outcomes) {
                results.add(outcome.get(60, java.util.concurrent.TimeUnit.SECONDS));
            }

            assertThat(results).containsExactlyInAnyOrder("hidden", "BLOG_MODERATION_INVALID_TRANSITION");
            assertThat(count("select count(*) from blog_moderation_actions where target_id = :t", "t", post))
                    .isEqualTo(1);
            assertThat(statusOf("blog_posts", post)).isEqualTo("HIDDEN");
        } finally {
            pool.shutdownNow();
        }
    }

    // --- an author's save against a moderator's decision -----------------------------------------------------------

    @Test
    void anAuthorWhoOpenedThePostBeforeItWasHiddenCannotSaveOverTheModeratorsDecision() {
        UUID author = fixture.learner();
        UUID staff = fixture.staff("Mod");
        UUID post = insertPost(author);
        SignedIn.as(jdbc, author);
        long openedAt = postService.get(post).version();

        actOnPost(staff, post, BlogModerationActionType.HIDE);

        SignedIn.as(jdbc, author);
        assertThatThrownBy(() -> postService.update(post,
                new com.englow3.blog.dto.command.SavePostCommand(marker + " edited", "new text", List.of(), openedAt)))
                        .isInstanceOfSatisfying(ConflictException.class,
                                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_STALE"));
        assertThat(statusOf("blog_posts", post)).isEqualTo("HIDDEN");
    }
}
