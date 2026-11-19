package com.englow3.chat.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.englow3.chat.dto.response.ChatSocketFrame;
import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatSocketRegistry;
import com.englow3.shared.error.TooManyRequestsException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class ChatSocketHandlerTest {

    private final ChatSocketRegistry registry = mock(ChatSocketRegistry.class);
    private final ChatConversationService conversationService = mock(ChatConversationService.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final ChatSocketHandler handler = new ChatSocketHandler(registry, conversationService, rateLimiter,
            new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());

    private final WebSocketSession session = mock(WebSocketSession.class);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(session.getAttributes()).thenReturn(Map.of(ChatSocketHandshakeInterceptor.USER_ID, me));
    }

    private void receive(String payload) {
        assertThatCode(() -> handler.handleTextMessage(session, new TextMessage(payload))).doesNotThrowAnyException();
    }

    @Test
    void anOpenedSocketIsRegisteredAndAClosedOrBrokenOneIsRemoved() throws Exception {
        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.handleTransportError(session, new RuntimeException("reset"));

        verify(registry).add(me, session);
        verify(registry, times(2)).remove(me, session);
    }

    @Test
    void aTypingSignalIsRelayedToTheOtherPersonAsTheCaller() {
        when(conversationService.typingRecipient(me, conversationId)).thenReturn(Optional.of(other));

        receive("{\"conversationId\":\"%s\",\"active\":true}".formatted(conversationId));

        verify(registry).sendTo(other, new ChatSocketFrame.Typing(conversationId, me, true));
        verify(rateLimiter).check(me, "chat-typing", 30, Duration.ofMinutes(1));
    }

    @Test
    void stoppingIsRelayedToo() {
        when(conversationService.typingRecipient(me, conversationId)).thenReturn(Optional.of(other));

        receive("{\"conversationId\":\"%s\",\"active\":false}".formatted(conversationId));

        verify(registry).sendTo(other, new ChatSocketFrame.Typing(conversationId, me, false));
    }

    @Test
    void aFieldTheServerDoesNotKnowDoesNotSpoilTheFrame() {
        when(conversationService.typingRecipient(me, conversationId)).thenReturn(Optional.of(other));

        receive("{\"conversationId\":\"%s\",\"active\":true,\"emoji\":\"x\"}".formatted(conversationId));

        verify(registry).sendTo(other, new ChatSocketFrame.Typing(conversationId, me, true));
    }

    @Test
    void brokenOrIncompleteFramesAreDroppedSilently() {
        for (String payload : new String[] { "not json", "", "null", "[]", "\"text\"", "{}", "{\"active\":true}",
                "{\"conversationId\":\"not-a-uuid\",\"active\":true}", "{\"conversationId\":null}" }) {
            receive(payload);
        }

        verifyNoInteractions(registry);
        verify(conversationService, never()).typingRecipient(any(), any());
    }

    @Test
    void aJsonNullFrameIsDroppedBeforeItIsValidated() {
        // Validating null throws, and the failure would be logged for every such frame before the rate limiter ran.
        Validator validator = mock(Validator.class);
        ChatSocketHandler strict = new ChatSocketHandler(registry, conversationService, rateLimiter, new ObjectMapper(),
                validator);

        assertThatCode(() -> strict.handleTextMessage(session, new TextMessage("null"))).doesNotThrowAnyException();

        verifyNoInteractions(validator, rateLimiter, registry);
        verify(conversationService, never()).typingRecipient(any(), any());
    }

    @Test
    void aSenderOverTheRateLimitIsDroppedSilently() {
        doThrow(new TooManyRequestsException("RATE_LIMITED", "slow down")).when(rateLimiter).check(eq(me), anyString(),
                anyInt(), any());

        receive("{\"conversationId\":\"%s\",\"active\":true}".formatted(conversationId));

        verifyNoInteractions(registry);
        verify(conversationService, never()).typingRecipient(any(), any());
    }

    @Test
    void noRecipientMeansNothingIsSent() {
        when(conversationService.typingRecipient(me, conversationId)).thenReturn(Optional.empty());

        receive("{\"conversationId\":\"%s\",\"active\":true}".formatted(conversationId));

        verify(registry, never()).sendTo(any(), any());
    }

    @Test
    void aFailureThatIsNotTheClientsDoesNotEscapeEither() {
        when(conversationService.typingRecipient(me, conversationId)).thenThrow(new IllegalStateException("db down"));

        receive("{\"conversationId\":\"%s\",\"active\":true}".formatted(conversationId));

        verify(registry, never()).sendTo(any(), any());
    }
}
