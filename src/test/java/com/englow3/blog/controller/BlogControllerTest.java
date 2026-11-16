package com.englow3.blog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.englow3.blog.dto.command.ListPostsCommand;
import com.englow3.blog.dto.command.ReportContentCommand;
import com.englow3.blog.dto.command.SavePostCommand;
import com.englow3.blog.dto.result.BlogAuthorResult;
import com.englow3.blog.dto.result.BlogCommentResult;
import com.englow3.blog.dto.result.BlogImageResult;
import com.englow3.blog.dto.result.BlogImageUploadTicket;
import com.englow3.blog.dto.result.BlogPostDetailResult;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.BlogReportResult;
import com.englow3.blog.dto.result.BlogTagCatalogue;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.dto.result.LikeStateResult;
import com.englow3.blog.dto.result.ReportOutcome;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogPostSort;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogSkill;
import com.englow3.blog.entity.BlogTagKind;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.service.BlogCommentService;
import com.englow3.blog.service.BlogImageService;
import com.englow3.blog.service.BlogLikeService;
import com.englow3.blog.service.BlogPostService;
import com.englow3.blog.service.BlogReportService;
import com.englow3.config.SecurityConfig;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

/**
 * The HTTP behaviour the services cannot show, and the contract the web plan builds on: which status each route
 * answers, the field names of every response, what a request is refused for before any service runs, how a domain
 * failure becomes a status, and that nothing here is open without signing in.
 */
@WebMvcTest(controllers = { BlogPostController.class, BlogCommentController.class, BlogImageController.class,
        BlogReportController.class }, properties = { "SUPABASE_ISSUER_URI=https://issuer.example.test/auth/v1",
                "SUPABASE_JWKS_URI=https://issuer.example.test/auth/v1/.well-known/jwks.json" })
@Import(SecurityConfig.class)
class BlogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDirectory userDirectory;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BlogPostService postService;

    @MockitoBean
    private BlogCommentService commentService;

    @MockitoBean
    private BlogLikeService likeService;

    @MockitoBean
    private BlogImageService imageService;

    @MockitoBean
    private BlogReportService reportService;

    private final UUID postId = UUID.randomUUID();
    private final UUID commentId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");

    private static final String VALID_REPORT = "{\"reason\":\"SPAM\",\"detail\":\"an advert\"}";

    private static final String VALID_POST = """
            {"title":"A title","body":"Some **Markdown**","tags":[{"kind":"SKILL","value":"WRITING"}]}""";

    private MockHttpServletRequestBuilder asLearner(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LEARNER")));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return asLearner(request).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private BlogPostDetailResult detail() {
        return new BlogPostDetailResult(postId, "A title", "Some **Markdown**",
                List.of(new BlogImageResult(UUID.randomUUID(), "https://storage.example/a.png")),
                new BlogAuthorResult(authorId, "Lan", null), List.of(new BlogTagResult(BlogTagKind.SKILL, "WRITING")),
                BlogPostStatus.PUBLISHED, 3, 4, 2, true, true, true, now, null, null);
    }

    private BlogPostSummaryResult summary() {
        return new BlogPostSummaryResult(postId, "A title", "An excerpt", new BlogAuthorResult(authorId, "Lan", null),
                BlogPostStatus.HIDDEN, List.of(new BlogTagResult(BlogTagKind.LEVEL, "B2")), 4, 2, true, now, now);
    }

    private BlogCommentResult comment() {
        return new BlogCommentResult(commentId, postId, new BlogAuthorResult(authorId, "Lan", "https://a/b.png"),
                "A comment", BlogCommentStatus.HIDDEN, false, now, null);
    }

    // --- nothing is public -----------------------------------------------------------------------------------------

    @Test
    void everyRouteRefusesARequestThatIsNotSignedIn() throws Exception {
        mockMvc.perform(get("/api/blog/posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/blog/posts/{id}", postId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/blog/posts/{id}/comments", postId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/blog/tags")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/blog/posts").contentType(MediaType.APPLICATION_JSON).content(VALID_POST))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/blog/posts/{id}/like", postId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/blog/images").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/png\",\"contentLength\":10}")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/blog/posts/{id}/reports", postId).contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPORT)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/blog/comments/{id}/reports", commentId).contentType(MediaType.APPLICATION_JSON)
                .content(VALID_REPORT)).andExpect(status().isUnauthorized());

        verifyNoInteractions(postService, commentService, likeService, imageService, reportService);
    }

    @Test
    void staffAndAdminUseTheSameRoutesAsLearners() throws Exception {
        when(postService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        for (String role : new String[] { "ROLE_STAFF", "ROLE_ADMIN" }) {
            mockMvc.perform(get("/api/blog/posts").with(jwt().authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isOk());
        }
    }

    // --- posts -----------------------------------------------------------------------------------------------------

    @Test
    void creatingAPostAnswers201WithTheDetailAndPassesTheFieldsOn() throws Exception {
        when(postService.create(any())).thenReturn(detail());

        mockMvc.perform(json(post("/api/blog/posts"), VALID_POST)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(postId.toString())).andExpect(jsonPath("$.title").value("A title"))
                .andExpect(jsonPath("$.body").value("Some **Markdown**"))
                .andExpect(jsonPath("$.images[0].url").value("https://storage.example/a.png"))
                .andExpect(jsonPath("$.images[0].id").exists())
                .andExpect(jsonPath("$.author.userId").value(authorId.toString()))
                .andExpect(jsonPath("$.author.displayName").value("Lan"))
                .andExpect(jsonPath("$.author.avatarUrl").isEmpty())
                .andExpect(jsonPath("$.tags[0].kind").value("SKILL"))
                .andExpect(jsonPath("$.tags[0].value").value("WRITING"))
                .andExpect(jsonPath("$.status").value("PUBLISHED")).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.likeCount").value(4)).andExpect(jsonPath("$.commentCount").value(2))
                .andExpect(jsonPath("$.likedByMe").value(true)).andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canInteract").value(true)).andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.editedAt").isEmpty());

        ArgumentCaptor<SavePostCommand> command = ArgumentCaptor.forClass(SavePostCommand.class);
        verify(postService).create(command.capture());
        assertThat(command.getValue().title()).isEqualTo("A title");
        assertThat(command.getValue().body()).isEqualTo("Some **Markdown**");
        assertThat(command.getValue().tags()).containsExactly(new SavePostCommand.Tag(BlogTagKind.SKILL, "WRITING"));
        assertThat(command.getValue().expectedVersion()).isNull();
    }

    @Test
    void aPostThatBreaksTheLimitsOrTheShapeIsRefusedBeforeAnyServiceRuns() throws Exception {
        String longTitle = "x".repeat(201);
        String nineTags = "{\"kind\":\"SKILL\",\"value\":\"WRITING\"},".repeat(8)
                + "{\"kind\":\"SKILL\",\"value\":\"READING\"}";
        for (String body : new String[] { "{}", "{\"title\":\"\",\"body\":\"b\",\"tags\":[]}",
                "{\"title\":\"t\",\"body\":\"  \",\"tags\":[]}", "{\"title\":\"t\",\"body\":\"b\"}",
                "{\"title\":\"%s\",\"body\":\"b\",\"tags\":[]}".formatted(longTitle),
                "{\"title\":\"t\",\"body\":\"%s\",\"tags\":[]}".formatted("x".repeat(20_001)),
                "{\"title\":\"t\",\"body\":\"b\",\"tags\":[%s]}".formatted(nineTags),
                "{\"title\":\"t\",\"body\":\"b\",\"tags\":[null]}",
                "{\"title\":\"t\",\"body\":\"b\",\"tags\":[{\"kind\":\"SKILL\"}]}",
                "{\"title\":\"t\",\"body\":\"b\",\"tags\":[{\"kind\":\"TOPIC\",\"value\":\"x\"}]}",
                "{\"title\":\"t\",\"body\":\"b\",\"tags\":[{\"kind\":\"SKILL\",\"value\":\"" + "y".repeat(31)
                        + "\"}]}" }) {
            mockMvc.perform(json(post("/api/blog/posts"), body)).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(postService);
    }

    @Test
    void anUnknownTagValueIsTheEntitysRefusalAndBecomesA400WithItsCode() throws Exception {
        when(postService.create(any())).thenThrow(
                new com.englow3.shared.error.BadRequestException("BLOG_TAG_INVALID", "Unknown SKILL tag 'B2'"));

        mockMvc.perform(json(post("/api/blog/posts"),
                "{\"title\":\"t\",\"body\":\"b\",\"tags\":[{\"kind\":\"SKILL\",\"value\":\"B2\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BLOG_TAG_INVALID"));
    }

    @Test
    void readingAPostAnswers200AndAMissingOneIsA404WithTheCode() throws Exception {
        when(postService.get(postId)).thenReturn(detail());
        UUID missing = UUID.randomUUID();
        when(postService.get(missing)).thenThrow(new NotFoundException("BLOG_POST_NOT_FOUND", "No post"));

        mockMvc.perform(asLearner(get("/api/blog/posts/{id}", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(postId.toString()));
        mockMvc.perform(asLearner(get("/api/blog/posts/{id}", missing))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BLOG_POST_NOT_FOUND"));
    }

    @Test
    void aPostWhoseAuthorIsErasedHasANullAuthorInTheResponse() throws Exception {
        BlogPostDetailResult d = detail();
        when(postService.get(postId)).thenReturn(new BlogPostDetailResult(d.id(), d.title(), d.body(), d.images(), null,
                d.tags(), d.status(), d.version(), d.likeCount(), d.commentCount(), d.likedByMe(), false, true,
                d.createdAt(), d.editedAt(), null));

        mockMvc.perform(asLearner(get("/api/blog/posts/{id}", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.author").isEmpty());
    }

    @Test
    void theModerationNoteIsInTheDetailAndIsNullWhenThereIsNone() throws Exception {
        BlogPostDetailResult d = detail();
        when(postService.get(postId)).thenReturn(new BlogPostDetailResult(d.id(), d.title(), d.body(), d.images(),
                d.author(), d.tags(), BlogPostStatus.HIDDEN, d.version(), d.likeCount(), d.commentCount(),
                d.likedByMe(), false, false, d.createdAt(), d.editedAt(), "Links to a shop"));
        UUID plain = UUID.randomUUID();
        when(postService.get(plain)).thenReturn(d);

        mockMvc.perform(asLearner(get("/api/blog/posts/{id}", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.moderationNote").value("Links to a shop"))
                // The fields that were already there are all still there under their own names.
                .andExpect(jsonPath("$.canEdit").value(false)).andExpect(jsonPath("$.canInteract").value(false))
                .andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.likeCount").value(4));
        mockMvc.perform(asLearner(get("/api/blog/posts/{id}", plain))).andExpect(status().isOk())
                .andExpect(jsonPath("$.moderationNote").isEmpty());
    }

    @Test
    void updatingNeedsTheVersionAndPassesItOn() throws Exception {
        when(postService.update(any(), any())).thenReturn(detail());

        mockMvc.perform(json(put("/api/blog/posts/{id}", postId), VALID_POST)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());
        mockMvc.perform(json(put("/api/blog/posts/{id}", postId),
                "{\"title\":\"A title\",\"body\":\"b\",\"tags\":[],\"version\":null}"))
                .andExpect(status().isBadRequest());
        verify(postService, never()).update(any(), any());

        mockMvc.perform(json(put("/api/blog/posts/{id}", postId),
                "{\"title\":\"A title\",\"body\":\"b\",\"tags\":[],\"version\":7}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));

        ArgumentCaptor<SavePostCommand> command = ArgumentCaptor.forClass(SavePostCommand.class);
        verify(postService).update(org.mockito.ArgumentMatchers.eq(postId), command.capture());
        assertThat(command.getValue().expectedVersion()).isEqualTo(7L);
    }

    @Test
    void aStaleVersionIsA409WithTheCode() throws Exception {
        when(postService.update(any(), any()))
                .thenThrow(new ConflictException("BLOG_POST_STALE", "The post changed since you opened it"));

        mockMvc.perform(json(put("/api/blog/posts/{id}", postId),
                "{\"title\":\"A title\",\"body\":\"b\",\"tags\":[],\"version\":1}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOG_POST_STALE"));
    }

    @Test
    void deletingAPostAnswers204() throws Exception {
        mockMvc.perform(asLearner(delete("/api/blog/posts/{id}", postId))).andExpect(status().isNoContent());

        verify(postService).delete(postId);
    }

    // --- the list --------------------------------------------------------------------------------------------------

    @Test
    void theListAnswersAPageWithEveryFieldTheCardsNeed() throws Exception {
        when(postService.list(any(), any())).thenReturn(new PageImpl<>(List.of(summary()), PageRequest.of(0, 20), 41));

        mockMvc.perform(asLearner(get("/api/blog/posts"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(postId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("A title"))
                .andExpect(jsonPath("$.items[0].excerpt").value("An excerpt"))
                .andExpect(jsonPath("$.items[0].author.displayName").value("Lan"))
                .andExpect(jsonPath("$.items[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.items[0].tags[0].kind").value("LEVEL"))
                .andExpect(jsonPath("$.items[0].likeCount").value(4))
                .andExpect(jsonPath("$.items[0].commentCount").value(2))
                .andExpect(jsonPath("$.items[0].likedByMe").value(true))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].editedAt").exists()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalItems").value(41))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void theListPassesItsFiltersSortAndPagingOn() throws Exception {
        when(postService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(asLearner(get("/api/blog/posts")).param("q", "tips").param("skill", "WRITING")
                .param("level", "B2").param("certificate", "IELTS").param("authorId", authorId.toString())
                .param("sort", "MOST_LIKED").param("page", "2").param("size", "10")).andExpect(status().isOk());

        ArgumentCaptor<ListPostsCommand> command = ArgumentCaptor.forClass(ListPostsCommand.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).list(command.capture(), page.capture());
        assertThat(command.getValue().q()).isEqualTo("tips");
        assertThat(command.getValue().skill()).isEqualTo(BlogSkill.WRITING);
        assertThat(command.getValue().level().name()).isEqualTo("B2");
        assertThat(command.getValue().certificate().name()).isEqualTo("IELTS");
        assertThat(command.getValue().authorId()).isEqualTo(authorId);
        assertThat(command.getValue().sort()).isEqualTo(BlogPostSort.MOST_LIKED);
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void withNoParametersTheListIsNewestFirstAndTheFirstPageOfTwenty() throws Exception {
        when(postService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(asLearner(get("/api/blog/posts"))).andExpect(status().isOk());

        ArgumentCaptor<ListPostsCommand> command = ArgumentCaptor.forClass(ListPostsCommand.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).list(command.capture(), page.capture());
        assertThat(command.getValue().sort()).isEqualTo(BlogPostSort.NEWEST);
        assertThat(command.getValue().skill()).isNull();
        assertThat(command.getValue().authorId()).isNull();
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void aNegativePageOrASizeOfZeroIsReadAsTheNearestValidOne() throws Exception {
        when(postService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(asLearner(get("/api/blog/posts")).param("page", "-4").param("size", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).list(any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void aFilterValueThatIsNotOneOfTheEnumsIsA400() throws Exception {
        mockMvc.perform(asLearner(get("/api/blog/posts")).param("skill", "ASTROLOGY"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asLearner(get("/api/blog/posts")).param("sort", "RANDOM")).andExpect(status().isBadRequest());
        mockMvc.perform(asLearner(get("/api/blog/posts")).param("authorId", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postService);
    }

    @Test
    void aSearchThatIsTooShortIsA400WithTheCode() throws Exception {
        when(postService.list(any(), any())).thenThrow(new com.englow3.shared.error.BadRequestException(
                "SEARCH_TERM_TOO_SHORT", "Type at least 2 characters"));

        mockMvc.perform(asLearner(get("/api/blog/posts")).param("q", "a")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_TERM_TOO_SHORT"));
    }

    @Test
    void theTagCatalogueListsTheValuesOfEachKind() throws Exception {
        when(postService.tagCatalogue())
                .thenReturn(new BlogTagCatalogue(List.of("WRITING"), List.of("B2"), List.of("IELTS")));

        mockMvc.perform(asLearner(get("/api/blog/tags"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0]").value("WRITING")).andExpect(jsonPath("$.levels[0]").value("B2"))
                .andExpect(jsonPath("$.certificates[0]").value("IELTS"));
    }

    // --- likes -----------------------------------------------------------------------------------------------------

    @Test
    void likingAndUnlikingAnswer200WithTheStateAndCount() throws Exception {
        when(likeService.like(postId)).thenReturn(new LikeStateResult(true, 5));
        when(likeService.unlike(postId)).thenReturn(new LikeStateResult(false, 4));

        mockMvc.perform(asLearner(put("/api/blog/posts/{id}/like", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(5));
        mockMvc.perform(asLearner(delete("/api/blog/posts/{id}/like", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(4));
    }

    // --- comments --------------------------------------------------------------------------------------------------

    @Test
    void theCommentListAnswersAPageWithTheStatusOfEach() throws Exception {
        when(commentService.list(any(), any()))
                .thenReturn(new PageImpl<>(List.of(comment()), PageRequest.of(0, 20), 1));

        mockMvc.perform(asLearner(get("/api/blog/posts/{id}/comments", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(commentId.toString()))
                .andExpect(jsonPath("$.items[0].postId").value(postId.toString()))
                .andExpect(jsonPath("$.items[0].author.avatarUrl").value("https://a/b.png"))
                .andExpect(jsonPath("$.items[0].body").value("A comment"))
                .andExpect(jsonPath("$.items[0].status").value("HIDDEN"))
                .andExpect(jsonPath("$.items[0].canEdit").value(false))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].editedAt").isEmpty()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void writingACommentAnswers201AndEditingAnswers200() throws Exception {
        when(commentService.create(any(), any())).thenReturn(comment());
        when(commentService.update(any(), any())).thenReturn(comment());

        mockMvc.perform(json(post("/api/blog/posts/{id}/comments", postId), "{\"body\":\"Nice\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(commentId.toString()));
        mockMvc.perform(json(put("/api/blog/comments/{id}", commentId), "{\"body\":\"Nicer\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<com.englow3.blog.dto.command.SaveCommentCommand> created = ArgumentCaptor
                .forClass(com.englow3.blog.dto.command.SaveCommentCommand.class);
        verify(commentService).create(org.mockito.ArgumentMatchers.eq(postId), created.capture());
        assertThat(created.getValue().body()).isEqualTo("Nice");
        verify(commentService).update(org.mockito.ArgumentMatchers.eq(commentId), any());
    }

    @Test
    void aBlankOrTooLongCommentIsRefusedBeforeAnyServiceRuns() throws Exception {
        for (String body : new String[] { "{}", "{\"body\":\"\"}", "{\"body\":\"   \"}",
                "{\"body\":\"%s\"}".formatted("x".repeat(2_001)) }) {
            mockMvc.perform(json(post("/api/blog/posts/{id}/comments", postId), body))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(put("/api/blog/comments/{id}", commentId), body)).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(commentService);
    }

    @Test
    void deletingACommentAnswers204() throws Exception {
        mockMvc.perform(asLearner(delete("/api/blog/comments/{id}", commentId))).andExpect(status().isNoContent());

        verify(commentService).delete(commentId);
    }

    // --- reports ---------------------------------------------------------------------------------------------------

    private BlogReportResult report() {
        return new BlogReportResult(reportId, BlogTargetType.POST, postId, BlogReportReason.SPAM, "an advert",
                BlogReportStatus.OPEN, now);
    }

    @Test
    void aNewReportAnswers201AndAnExistingOneAnswers200WithTheSameShape() throws Exception {
        when(reportService.reportPost(any(), any())).thenReturn(new ReportOutcome(report(), true))
                .thenReturn(new ReportOutcome(report(), false));
        when(reportService.reportComment(any(), any())).thenReturn(new ReportOutcome(report(), true))
                .thenReturn(new ReportOutcome(report(), false));

        for (int attempt = 0; attempt < 2; attempt++) {
            var expected = attempt == 0 ? status().isCreated() : status().isOk();
            mockMvc.perform(json(post("/api/blog/posts/{id}/reports", postId), VALID_REPORT)).andExpect(expected)
                    .andExpect(jsonPath("$.id").value(reportId.toString()))
                    .andExpect(jsonPath("$.targetType").value("POST"))
                    .andExpect(jsonPath("$.targetId").value(postId.toString()))
                    .andExpect(jsonPath("$.reason").value("SPAM")).andExpect(jsonPath("$.detail").value("an advert"))
                    .andExpect(jsonPath("$.status").value("OPEN")).andExpect(jsonPath("$.createdAt").exists());
            mockMvc.perform(json(post("/api/blog/comments/{id}/reports", commentId), VALID_REPORT)).andExpect(expected)
                    .andExpect(jsonPath("$.id").value(reportId.toString()));
        }
    }

    @Test
    void theReasonAndTheNoteArePassedOnAndTheNoteMayBeLeftOut() throws Exception {
        when(reportService.reportPost(any(), any())).thenReturn(new ReportOutcome(report(), true));

        mockMvc.perform(json(post("/api/blog/posts/{id}/reports", postId), "{\"reason\":\"MISINFORMATION\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<ReportContentCommand> command = ArgumentCaptor.forClass(ReportContentCommand.class);
        verify(reportService).reportPost(org.mockito.ArgumentMatchers.eq(postId), command.capture());
        assertThat(command.getValue().reason()).isEqualTo(BlogReportReason.MISINFORMATION);
        assertThat(command.getValue().detail()).isNull();
    }

    @Test
    void aReportWithoutAReasonOrWithAWrongOneOrAnOverlongNoteIsRefusedBeforeAnyServiceRuns() throws Exception {
        for (String body : new String[] { "{}", "{\"detail\":\"no reason\"}", "{\"reason\":null}",
                "{\"reason\":\"THEFT\"}", "{\"reason\":\"spam\"}",
                "{\"reason\":\"SPAM\",\"detail\":\"%s\"}".formatted("x".repeat(501)) }) {
            mockMvc.perform(json(post("/api/blog/posts/{id}/reports", postId), body))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(json(post("/api/blog/comments/{id}/reports", commentId), body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(reportService);
    }

    @Test
    void theServicesRefusalsOfAReportKeepTheirStatusAndCode() throws Exception {
        when(reportService.reportPost(any(), any())).thenThrow(new NotFoundException("BLOG_POST_NOT_FOUND", "No post"))
                .thenThrow(new com.englow3.shared.error.BadRequestException("BLOG_REPORT_OWN_CONTENT", "Your own"))
                .thenThrow(new com.englow3.shared.error.TooManyRequestsException("RATE_LIMITED", "Slow down"));

        mockMvc.perform(json(post("/api/blog/posts/{id}/reports", postId), VALID_REPORT))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BLOG_POST_NOT_FOUND"));
        mockMvc.perform(json(post("/api/blog/posts/{id}/reports", postId), VALID_REPORT))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BLOG_REPORT_OWN_CONTENT"));
        mockMvc.perform(json(post("/api/blog/posts/{id}/reports", postId), VALID_REPORT))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    // --- images ----------------------------------------------------------------------------------------------------

    @Test
    void startingAnUploadAnswers201WithTheUrlAndTheReferenceToWriteInTheMarkdown() throws Exception {
        UUID imageId = UUID.randomUUID();
        when(imageService.startUpload(any())).thenReturn(new BlogImageUploadTicket(imageId,
                "https://storage.example/put", "image/png", 600, "blog-image:" + imageId));

        mockMvc.perform(json(post("/api/blog/images"), "{\"contentType\":\"image/png\",\"contentLength\":1234}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.imageId").value(imageId.toString()))
                .andExpect(jsonPath("$.uploadUrl").value("https://storage.example/put"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600))
                .andExpect(jsonPath("$.reference").value("blog-image:" + imageId));
    }

    @Test
    void anUploadRequestWithoutATypeOrWithAnEmptySizeIsRefused() throws Exception {
        for (String body : new String[] { "{}", "{\"contentType\":\"\",\"contentLength\":10}",
                "{\"contentType\":\"image/png\",\"contentLength\":0}",
                "{\"contentType\":\"image/png\",\"contentLength\":-5}" }) {
            mockMvc.perform(json(post("/api/blog/images"), body)).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(imageService);
    }
}
