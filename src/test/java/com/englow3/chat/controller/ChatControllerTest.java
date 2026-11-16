package com.englow3.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
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

import com.englow3.chat.dto.result.ChatImageUploadTicket;
import com.englow3.chat.dto.result.ChatMessageResult;
import com.englow3.chat.dto.result.ConversationSummaryResult;
import com.englow3.chat.dto.result.OpenConversationOutcome;
import com.englow3.chat.dto.result.SendMessageOutcome;
import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatImageService;
import com.englow3.chat.service.ChatMessageService;
import com.englow3.config.SecurityConfig;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

/**
 * The HTTP behaviour the services cannot show: 201 against 200, 204, what a request is refused for before any service
 * runs, how a domain failure becomes a status, and the bound on the page size.
 */
@WebMvcTest(controllers = { ChatConversationController.class, ChatMessageController.class }, properties = {
        "SUPABASE_ISSUER_URI=https://issuer.example.test/auth/v1",
        "SUPABASE_JWKS_URI=https://issuer.example.test/auth/v1/.well-known/jwks.json" })
@Import(SecurityConfig.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDirectory userDirectory;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ChatConversationService conversationService;

    @MockitoBean
    private ChatImageService imageService;

    @MockitoBean
    private ChatMessageService messageService;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID friendId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");

    private MockHttpServletRequestBuilder asLearner(MockHttpServletRequestBuilder request) {
        return request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_LEARNER")));
    }

    private ConversationSummaryResult summary() {
        return new ConversationSummaryResult(conversationId,
                new ConversationSummaryResult.OtherUser(friendId, "Lan", null), 3, 1, 2,
                new ConversationSummaryResult.LastMessage(3, friendId, "hello", false, false, now), true);
    }

    private ChatMessageResult message() {
        return new ChatMessageResult(UUID.randomUUID(), conversationId, 4, UUID.randomUUID(), "hi", null, false, now,
                "client-1");
    }

    @Test
    void aRequestWithoutASignInIsRefused() throws Exception {
        mockMvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/chat/unread-count")).andExpect(status().isUnauthorized());

        verifyNoInteractions(conversationService);
    }

    @Test
    void openingANewConversationAnswers201AndAnExistingOneAnswers200() throws Exception {
        String body = "{\"friendId\":\"%s\"}".formatted(friendId);
        when(conversationService.open(friendId)).thenReturn(new OpenConversationOutcome(summary(), true))
                .thenReturn(new OpenConversationOutcome(summary(), false));

        mockMvc.perform(
                asLearner(post("/api/chat/conversations")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.otherUser.displayName").value("Lan"))
                .andExpect(jsonPath("$.unreadCount").value(2)).andExpect(jsonPath("$.canSend").value(true))
                .andExpect(jsonPath("$.lastMessage.preview").value("hello"));
        mockMvc.perform(
                asLearner(post("/api/chat/conversations")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void openingWithoutAFriendIdIsABadRequest() throws Exception {
        mockMvc.perform(
                asLearner(post("/api/chat/conversations")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.friendId").exists());

        verifyNoInteractions(conversationService);
    }

    @Test
    void theListIsBoundedToFiftyAConversationsAndTheCallersSortIsIgnored() throws Exception {
        when(conversationService.list(any())).thenReturn(new PageImpl<>(List.of(summary()), PageRequest.of(0, 50), 1));

        mockMvc.perform(asLearner(get("/api/chat/conversations")).param("size", "500").param("sort", "id,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(conversationId.toString()))
                .andExpect(jsonPath("$.totalItems").value(1));

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(conversationService).list(page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(50);
        assertThat(page.getValue().getSort().isUnsorted()).isTrue();
    }

    @Test
    void aConversationTheCallerIsNotInIsA404WithTheErrorCode() throws Exception {
        when(conversationService.get(conversationId))
                .thenThrow(new NotFoundException("CHAT_CONVERSATION_NOT_FOUND", "No conversation"));

        mockMvc.perform(asLearner(get("/api/chat/conversations/{id}", conversationId))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_CONVERSATION_NOT_FOUND"));
    }

    @Test
    void markingReadAnswers204AndPassesTheSeqOn() throws Exception {
        mockMvc.perform(asLearner(post("/api/chat/conversations/{id}/read", conversationId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"upToSeq\":7}")).andExpect(status().isNoContent());

        verify(conversationService).markRead(conversationId, 7L);
    }

    @Test
    void aMissingOrNegativeUpToSeqIsRefusedAndNeverReadAsZero() throws Exception {
        mockMvc.perform(asLearner(post("/api/chat/conversations/{id}/read", conversationId))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.upToSeq").exists());
        mockMvc.perform(asLearner(post("/api/chat/conversations/{id}/read", conversationId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"upToSeq\":-1}"))
                .andExpect(status().isBadRequest());

        verify(conversationService, never()).markRead(any(), anyLong());
    }

    @Test
    void sendingAnswers201AndARetriedClientIdAnswers200() throws Exception {
        when(messageService.send(any())).thenReturn(new SendMessageOutcome(message(), true))
                .thenReturn(new SendMessageOutcome(message(), false));
        String body = "{\"clientId\":\"client-1\",\"body\":\"hi\"}";

        mockMvc.perform(asLearner(post("/api/chat/conversations/{id}/messages", conversationId))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.seq").value(4)).andExpect(jsonPath("$.body").value("hi"))
                .andExpect(jsonPath("$.clientId").value("client-1"));
        mockMvc.perform(asLearner(post("/api/chat/conversations/{id}/messages", conversationId))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    @Test
    void theCommandCarriesThePathConversationAndTheRequestFields() throws Exception {
        UUID uploadId = UUID.randomUUID();
        when(messageService.send(any())).thenReturn(new SendMessageOutcome(message(), true));

        mockMvc.perform(asLearner(post("/api/chat/conversations/{id}/messages", conversationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"client-9\",\"imageUploadId\":\"%s\"}".formatted(uploadId)))
                .andExpect(status().isCreated());

        var command = ArgumentCaptor.forClass(com.englow3.chat.dto.command.SendMessageCommand.class);
        verify(messageService).send(command.capture());
        assertThat(command.getValue().conversationId()).isEqualTo(conversationId);
        assertThat(command.getValue().clientId()).isEqualTo("client-9");
        assertThat(command.getValue().body()).isNull();
        assertThat(command.getValue().imageUploadId()).isEqualTo(uploadId);
    }

    @Test
    void aMessageWithNoClientIdOrAnOverlongOneIsRefusedBeforeTheServiceRuns() throws Exception {
        String path = "/api/chat/conversations/{id}/messages";
        mockMvc.perform(asLearner(post(path, conversationId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.clientId").exists());
        mockMvc.perform(asLearner(post(path, conversationId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"body\":\"hi\"}".formatted("c".repeat(65))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(asLearner(post(path, conversationId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"client-1\",\"body\":\"%s\"}".formatted("x".repeat(4001))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.body").exists());

        verifyNoInteractions(messageService);
    }

    @Test
    void aRefusedSendBecomesItsStatusAndCode() throws Exception {
        when(messageService.send(any())).thenThrow(new ForbiddenException("CHAT_NOT_FRIENDS", "Not friends"));

        mockMvc.perform(asLearner(post("/api/chat/conversations/{id}/messages", conversationId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"clientId\":\"client-1\",\"body\":\"hi\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHAT_NOT_FRIENDS"));
    }

    @Test
    void historyDefaultsToFiftyAndPassesTheCursorsOn() throws Exception {
        mockMvc.perform(asLearner(get("/api/chat/conversations/{id}/messages", conversationId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mockMvc.perform(asLearner(get("/api/chat/conversations/{id}/messages", conversationId)).param("afterSeq", "5")
                .param("limit", "20")).andExpect(status().isOk());
        mockMvc.perform(asLearner(get("/api/chat/conversations/{id}/messages", conversationId)).param("beforeSeq", "9"))
                .andExpect(status().isOk());

        verify(messageService).history(conversationId, null, null, 50);
        verify(messageService).history(conversationId, 5L, null, 20);
        verify(messageService).history(conversationId, null, 9L, 50);
    }

    @Test
    void recallAnswers200WithTheRecalledMessage() throws Exception {
        UUID messageId = UUID.randomUUID();
        when(messageService.recall(messageId)).thenReturn(new ChatMessageResult(messageId, conversationId, 4,
                UUID.randomUUID(), null, null, true, now, "client-1"));

        mockMvc.perform(asLearner(post("/api/chat/messages/{id}/recall", messageId))).andExpect(status().isOk())
                .andExpect(jsonPath("$.recalled").value(true)).andExpect(jsonPath("$.id").value(messageId.toString()));
    }

    @Test
    void anImageUploadAnswers201WithTheUrlAndRefusesNonsenseBeforeTheServiceRuns() throws Exception {
        UUID uploadId = UUID.randomUUID();
        when(imageService.startUpload(any()))
                .thenReturn(new ChatImageUploadTicket(uploadId, "https://storage.example/put", "image/png", 600));
        String path = "/api/chat/conversations/{id}/image-uploads";

        mockMvc.perform(asLearner(post(path, conversationId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/png\",\"contentLength\":1000}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").value(uploadId.toString()))
                .andExpect(jsonPath("$.uploadUrl").value("https://storage.example/put"))
                .andExpect(jsonPath("$.expiresInSeconds").value(600));
        mockMvc.perform(asLearner(post(path, conversationId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"\",\"contentLength\":1000}")).andExpect(status().isBadRequest());
        mockMvc.perform(asLearner(post(path, conversationId)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/png\",\"contentLength\":0}")).andExpect(status().isBadRequest());

        verify(imageService).startUpload(any());
    }

    @Test
    void theUnreadBadgeIsACount() throws Exception {
        when(conversationService.unreadConversationCount()).thenReturn(4L);

        mockMvc.perform(asLearner(get("/api/chat/unread-count"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }
}
