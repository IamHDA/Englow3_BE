package com.englow3.blog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import com.englow3.blog.dto.command.ModerationActionCommand;
import com.englow3.blog.dto.command.ModerationQueueFilter;
import com.englow3.blog.dto.result.BlogAuthorResult;
import com.englow3.blog.dto.result.BlogImageResult;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.dto.result.ModerationActionResult;
import com.englow3.blog.dto.result.ModerationQueueItemResult;
import com.englow3.blog.dto.result.ModerationTargetResult;
import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogQueueSort;
import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTagKind;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.service.BlogModerationService;
import com.englow3.config.SecurityConfig;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

/**
 * The security of moderation is the behaviour that matters here: a learner is turned away from everything under
 * {@code /api/admin/blog} and Staff and Admin are let in, with the field names of every response, which the web plan
 * builds on. The service is a mock; what a moderator may do is the service's.
 */
@WebMvcTest(controllers = AdminBlogModerationController.class, properties = {
        "SUPABASE_ISSUER_URI=https://issuer.example.test/auth/v1",
        "SUPABASE_JWKS_URI=https://issuer.example.test/auth/v1/.well-known/jwks.json" })
@Import(SecurityConfig.class)
class AdminBlogModerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDirectory userDirectory;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BlogModerationService moderationService;

    private final UUID postId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID actionId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");

    private static final String VALID_ACTION = "{\"action\":\"HIDE\",\"reason\":\"Advertising\"}";

    private MockHttpServletRequestBuilder as(String role, MockHttpServletRequestBuilder request) {
        // The back-office filter asks for the role the database holds now; it agrees with the token here.
        when(userDirectory.currentRole()).thenReturn(role);
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder request) {
        return as("STAFF", request);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private ModerationTargetResult target() {
        BlogAuthorResult author = new BlogAuthorResult(authorId, "Lan", "https://a/b.png");
        return new ModerationTargetResult(BlogTargetType.POST, postId, "HIDDEN",
                new ModerationTargetResult.Content("A title", "Some **Markdown**",
                        List.of(new BlogImageResult(UUID.randomUUID(), "https://storage.example/a.png")),
                        List.of(new BlogTagResult(BlogTagKind.SKILL, "WRITING")), null, now),
                author,
                List.of(new ModerationTargetResult.Report(reportId, author, BlogReportReason.SPAM, "an advert",
                        BlogReportStatus.RESOLVED, "Hidden", now, now)),
                List.of(new ModerationTargetResult.Action(actionId, BlogModerationActionType.HIDE, "Advertising",
                        author, now)));
    }

    // --- who may call ----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "/api/admin/blog/queue", "/api/admin/blog/actions",
            "/api/admin/blog/targets/post/00000000-0000-0000-0000-000000000001" })
    void aRequestThatIsNotSignedInIsRefusedEverywhere(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());

        verifyNoInteractions(moderationService);
    }

    @Test
    void aLearnerIsTurnedAwayFromEveryRouteWithAccessDeniedAndNoServiceRuns() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(get("/api/admin/blog/queue"),
                get("/api/admin/blog/actions"), get("/api/admin/blog/targets/post/{id}", postId),
                get("/api/admin/blog/targets/comment/{id}", postId),
                json(post("/api/admin/blog/targets/post/{id}/actions", postId), VALID_ACTION))) {
            mockMvc.perform(as("LEARNER", request)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        verifyNoInteractions(moderationService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "STAFF", "ADMIN" })
    void staffAndAdminAreLetInToEveryRoute(String role) throws Exception {
        when(moderationService.queue(any(), any())).thenReturn(new PageImpl<>(List.of()));
        when(moderationService.recentActions(any())).thenReturn(new PageImpl<>(List.of()));
        when(moderationService.target(any(), any())).thenReturn(target());
        when(moderationService.act(any(), any(), any())).thenReturn(target());

        mockMvc.perform(as(role, get("/api/admin/blog/queue"))).andExpect(status().isOk());
        mockMvc.perform(as(role, get("/api/admin/blog/actions"))).andExpect(status().isOk());
        mockMvc.perform(as(role, get("/api/admin/blog/targets/post/{id}", postId))).andExpect(status().isOk());
        mockMvc.perform(as(role, json(post("/api/admin/blog/targets/comment/{id}/actions", postId), VALID_ACTION)))
                .andExpect(status().isOk());
    }

    @Test
    void aTokenThatSaysStaffWhoseAccountIsNoLongerStaffIsRefused() throws Exception {
        when(userDirectory.currentRole()).thenReturn("LEARNER");

        mockMvc.perform(get("/api/admin/blog/queue").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STAFF"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_CHANGED"));

        verifyNoInteractions(moderationService);
    }

    // --- the type in the path --------------------------------------------------------------------------------------

    @Test
    void aTypeThatIsNeitherPostNorCommentIsA400WithItsCodeAndNoServiceRuns() throws Exception {
        for (String type : new String[] { "foo", "POST", "Comment", "posts", "user" }) {
            mockMvc.perform(asStaff(get("/api/admin/blog/targets/{type}/{id}", type, postId)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BLOG_TARGET_TYPE_INVALID"));
            mockMvc.perform(
                    asStaff(json(post("/api/admin/blog/targets/{type}/{id}/actions", type, postId), VALID_ACTION)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BLOG_TARGET_TYPE_INVALID"));
        }

        verifyNoInteractions(moderationService);
    }

    @Test
    void anIdThatIsNotAUuidIsA400() throws Exception {
        mockMvc.perform(asStaff(get("/api/admin/blog/targets/post/not-a-uuid"))).andExpect(status().isBadRequest());

        verifyNoInteractions(moderationService);
    }

    @Test
    void thePathTypeIsMappedToTheTargetTypeTheServiceGets() throws Exception {
        when(moderationService.target(any(), any())).thenReturn(target());

        mockMvc.perform(asStaff(get("/api/admin/blog/targets/post/{id}", postId))).andExpect(status().isOk());
        mockMvc.perform(asStaff(get("/api/admin/blog/targets/comment/{id}", postId))).andExpect(status().isOk());

        verify(moderationService).target(BlogTargetType.POST, postId);
        verify(moderationService).target(BlogTargetType.COMMENT, postId);
    }

    // --- the queue -------------------------------------------------------------------------------------------------

    @Test
    void theQueueAnswersAPageWithEveryFieldTheScreenNeeds() throws Exception {
        ModerationQueueItemResult item = new ModerationQueueItemResult(BlogTargetType.COMMENT, postId, "A post",
                "An excerpt", new BlogAuthorResult(authorId, "Lan", null), "VISIBLE", 3,
                List.of(BlogReportReason.SPAM, BlogReportReason.OTHER), now.minusSeconds(60), now);
        ModerationQueueItemResult deleted = new ModerationQueueItemResult(BlogTargetType.POST, reportId, null, null,
                null, "DELETED", 1, List.of(BlogReportReason.OTHER), now, now);
        when(moderationService.queue(any(), any()))
                .thenReturn(new PageImpl<>(List.of(item, deleted), PageRequest.of(0, 20), 41));

        mockMvc.perform(asStaff(get("/api/admin/blog/queue"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].targetType").value("COMMENT"))
                .andExpect(jsonPath("$.items[0].targetId").value(postId.toString()))
                .andExpect(jsonPath("$.items[0].title").value("A post"))
                .andExpect(jsonPath("$.items[0].excerpt").value("An excerpt"))
                .andExpect(jsonPath("$.items[0].author.displayName").value("Lan"))
                .andExpect(jsonPath("$.items[0].targetStatus").value("VISIBLE"))
                .andExpect(jsonPath("$.items[0].openReports").value(3))
                .andExpect(jsonPath("$.items[0].reasons[0]").value("SPAM"))
                .andExpect(jsonPath("$.items[0].reasons[1]").value("OTHER"))
                .andExpect(jsonPath("$.items[0].firstReportedAt").exists())
                .andExpect(jsonPath("$.items[0].lastReportedAt").exists())
                .andExpect(jsonPath("$.items[1].targetStatus").value("DELETED"))
                .andExpect(jsonPath("$.items[1].title").isEmpty()).andExpect(jsonPath("$.items[1].excerpt").isEmpty())
                .andExpect(jsonPath("$.items[1].author").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalItems").value(41))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void theQueuePassesItsFilterSortAndPagingOn() throws Exception {
        when(moderationService.queue(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(asStaff(get("/api/admin/blog/queue")).param("type", "COMMENT").param("sort", "MOST_REPORTED")
                .param("page", "2").param("size", "10")).andExpect(status().isOk());

        ArgumentCaptor<ModerationQueueFilter> filter = ArgumentCaptor.forClass(ModerationQueueFilter.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(moderationService).queue(filter.capture(), page.capture());
        assertThat(filter.getValue().type()).isEqualTo(BlogTargetType.COMMENT);
        assertThat(filter.getValue().sort()).isEqualTo(BlogQueueSort.MOST_REPORTED);
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void withNoParametersTheQueueIsOldestFirstOfBothKindsAndTheFirstPageOfTwenty() throws Exception {
        when(moderationService.queue(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(asStaff(get("/api/admin/blog/queue"))).andExpect(status().isOk());

        ArgumentCaptor<ModerationQueueFilter> filter = ArgumentCaptor.forClass(ModerationQueueFilter.class);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(moderationService).queue(filter.capture(), page.capture());
        assertThat(filter.getValue().type()).isNull();
        assertThat(filter.getValue().sort()).isEqualTo(BlogQueueSort.OLDEST);
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void aFilterValueThatIsNotOneOfTheEnumsIsA400() throws Exception {
        mockMvc.perform(asStaff(get("/api/admin/blog/queue")).param("type", "USER")).andExpect(status().isBadRequest());
        mockMvc.perform(asStaff(get("/api/admin/blog/queue")).param("sort", "NEWEST"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(moderationService);
    }

    @Test
    void aNegativePageOrASizeOfZeroIsReadAsTheNearestValidOne() throws Exception {
        when(moderationService.queue(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(asStaff(get("/api/admin/blog/queue")).param("page", "-4").param("size", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(moderationService).queue(any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(1);
    }

    // --- one piece of content --------------------------------------------------------------------------------------

    @Test
    void theTargetAnswersTheContentEveryReportAndTheHistoryWithTheirFieldNames() throws Exception {
        when(moderationService.target(BlogTargetType.POST, postId)).thenReturn(target());

        mockMvc.perform(asStaff(get("/api/admin/blog/targets/post/{id}", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("POST"))
                .andExpect(jsonPath("$.targetId").value(postId.toString()))
                .andExpect(jsonPath("$.targetStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.content.title").value("A title"))
                .andExpect(jsonPath("$.content.body").value("Some **Markdown**"))
                .andExpect(jsonPath("$.content.images[0].url").value("https://storage.example/a.png"))
                .andExpect(jsonPath("$.content.images[0].id").exists())
                .andExpect(jsonPath("$.content.tags[0].kind").value("SKILL"))
                .andExpect(jsonPath("$.content.tags[0].value").value("WRITING"))
                .andExpect(jsonPath("$.content.postId").isEmpty()).andExpect(jsonPath("$.content.createdAt").exists())
                .andExpect(jsonPath("$.author.userId").value(authorId.toString()))
                .andExpect(jsonPath("$.author.avatarUrl").value("https://a/b.png"))
                .andExpect(jsonPath("$.reports[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$.reports[0].reporter.displayName").value("Lan"))
                .andExpect(jsonPath("$.reports[0].reason").value("SPAM"))
                .andExpect(jsonPath("$.reports[0].detail").value("an advert"))
                .andExpect(jsonPath("$.reports[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.reports[0].resolutionNote").value("Hidden"))
                .andExpect(jsonPath("$.reports[0].createdAt").exists())
                .andExpect(jsonPath("$.reports[0].resolvedAt").exists())
                .andExpect(jsonPath("$.actions[0].id").value(actionId.toString()))
                .andExpect(jsonPath("$.actions[0].action").value("HIDE"))
                .andExpect(jsonPath("$.actions[0].reason").value("Advertising"))
                .andExpect(jsonPath("$.actions[0].moderator.displayName").value("Lan"))
                .andExpect(jsonPath("$.actions[0].createdAt").exists());
    }

    @Test
    void contentThatIsGoneHasNullContentAndAuthorInTheResponse() throws Exception {
        when(moderationService.target(any(), any())).thenReturn(
                new ModerationTargetResult(BlogTargetType.POST, postId, "DELETED", null, null, List.of(), List.of()));

        mockMvc.perform(asStaff(get("/api/admin/blog/targets/post/{id}", postId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.targetStatus").value("DELETED")).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.author").isEmpty()).andExpect(jsonPath("$.reports").isEmpty());
    }

    @Test
    void nothingToShowIsA404WithTheCode() throws Exception {
        when(moderationService.target(any(), any()))
                .thenThrow(new NotFoundException("BLOG_TARGET_NOT_FOUND", "No post"));

        mockMvc.perform(asStaff(get("/api/admin/blog/targets/post/{id}", postId))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BLOG_TARGET_NOT_FOUND"));
    }

    // --- acting ----------------------------------------------------------------------------------------------------

    @Test
    void actingAnswers200WithTheContentAsItIsAfterwardsAndPassesTheCommandOn() throws Exception {
        when(moderationService.act(any(), any(), any())).thenReturn(target());

        mockMvc.perform(asStaff(json(post("/api/admin/blog/targets/post/{id}/actions", postId), VALID_ACTION)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetStatus").value("HIDDEN"))
                .andExpect(jsonPath("$.actions[0].action").value("HIDE"));

        ArgumentCaptor<ModerationActionCommand> command = ArgumentCaptor.forClass(ModerationActionCommand.class);
        verify(moderationService).act(eq(BlogTargetType.POST), eq(postId), command.capture());
        assertThat(command.getValue().action()).isEqualTo(BlogModerationActionType.HIDE);
        assertThat(command.getValue().reason()).isEqualTo("Advertising");
    }

    @Test
    void allFourActionsAreAccepted() throws Exception {
        when(moderationService.act(any(), any(), any())).thenReturn(target());

        for (BlogModerationActionType action : BlogModerationActionType.values()) {
            mockMvc.perform(asStaff(json(post("/api/admin/blog/targets/comment/{id}/actions", postId),
                    "{\"action\":\"%s\",\"reason\":\"Because\"}".formatted(action)))).andExpect(status().isOk());
        }
    }

    @Test
    void anActionWithoutAReasonOrWithAWrongOneIsRefusedBeforeAnyServiceRuns() throws Exception {
        for (String body : new String[] { "{}", "{\"action\":\"HIDE\"}", "{\"reason\":\"Because\"}",
                "{\"action\":\"HIDE\",\"reason\":\"\"}", "{\"action\":\"HIDE\",\"reason\":\"   \"}",
                "{\"action\":\"HIDE\",\"reason\":null}",
                "{\"action\":\"HIDE\",\"reason\":\"%s\"}".formatted("x".repeat(501)),
                "{\"action\":\"DELETE\",\"reason\":\"Because\"}", "{\"action\":null,\"reason\":\"Because\"}" }) {
            mockMvc.perform(asStaff(json(post("/api/admin/blog/targets/post/{id}/actions", postId), body)))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(moderationService);
    }

    @Test
    void theServicesRefusalsKeepTheirStatusAndCode() throws Exception {
        when(moderationService.act(any(), any(), any()))
                .thenThrow(new ForbiddenException("BLOG_MODERATION_OWN_CONTENT", "Another moderator has to review"))
                .thenThrow(new ConflictException("BLOG_MODERATION_INVALID_TRANSITION", "Already hidden"))
                .thenThrow(new ConflictException("BLOG_NO_OPEN_REPORTS", "Nothing to dismiss"));

        mockMvc.perform(asStaff(json(post("/api/admin/blog/targets/post/{id}/actions", postId), VALID_ACTION)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLOG_MODERATION_OWN_CONTENT"));
        mockMvc.perform(asStaff(json(post("/api/admin/blog/targets/post/{id}/actions", postId), VALID_ACTION)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOG_MODERATION_INVALID_TRANSITION"));
        mockMvc.perform(asStaff(json(post("/api/admin/blog/targets/post/{id}/actions", postId), VALID_ACTION)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BLOG_NO_OPEN_REPORTS"));
    }

    // --- the history -----------------------------------------------------------------------------------------------

    @Test
    void theHistoryAnswersAPageOfActionsAndPassesThePagingOn() throws Exception {
        when(moderationService.recentActions(any())).thenReturn(new PageImpl<>(
                List.of(new ModerationActionResult(actionId, BlogTargetType.COMMENT, postId,
                        BlogModerationActionType.DISMISS, "Fine", new BlogAuthorResult(authorId, "Lan", null), now)),
                PageRequest.of(1, 5), 11));

        mockMvc.perform(asStaff(get("/api/admin/blog/actions")).param("page", "1").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(actionId.toString()))
                .andExpect(jsonPath("$.items[0].targetType").value("COMMENT"))
                .andExpect(jsonPath("$.items[0].targetId").value(postId.toString()))
                .andExpect(jsonPath("$.items[0].action").value("DISMISS"))
                .andExpect(jsonPath("$.items[0].reason").value("Fine"))
                .andExpect(jsonPath("$.items[0].moderator.displayName").value("Lan"))
                .andExpect(jsonPath("$.items[0].createdAt").exists()).andExpect(jsonPath("$.totalItems").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(moderationService).recentActions(page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(1);
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
    }
}
