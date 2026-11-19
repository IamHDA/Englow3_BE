package com.englow3.chat.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.englow3.chat.dto.request.TypingFrame;
import com.englow3.chat.dto.response.ChatSocketFrame;
import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatSocketRegistry;
import com.englow3.shared.error.DomainException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The chat socket is receive-only: messages are sent, read and recalled over REST. The one thing a client may send is a
 * typing signal, relayed to the other person and never stored. Anything else - broken JSON, an unknown shape, a signal
 * for a conversation the sender is not in - is dropped without closing the socket or logging an error: a typing signal
 * is disposable, and a lost one is covered by the viewer's own timer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSocketHandler extends TextWebSocketHandler {

    /**
     * Typing is a signal on every few keystrokes, not on every one; this is generous for a person and not for a loop.
     */
    static final int MAX_TYPING_FRAMES_PER_WINDOW = 30;
    static final Duration TYPING_WINDOW = Duration.ofMinutes(1);

    private final ChatSocketRegistry registry;
    private final ChatConversationService conversationService;
    private final UserRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.add(userId(session), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(userId(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.remove(userId(session), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID me = userId(session);
        try {
            TypingFrame frame = objectMapper.readValue(message.getPayload(), TypingFrame.class);
            // A JSON null frame deserialises to null, and validating null throws - which would log a warning for every
            // such frame before the rate limiter has run.
            if (frame == null || !validator.validate(frame).isEmpty()) {
                return;
            }
            rateLimiter.check(me, "chat-typing", MAX_TYPING_FRAMES_PER_WINDOW, TYPING_WINDOW);
            conversationService.typingRecipient(me, frame.conversationId()).ifPresent(other -> registry.sendTo(other,
                    new ChatSocketFrame.Typing(frame.conversationId(), me, frame.active())));
        } catch (JsonProcessingException | DomainException ignored) {
            // A typing signal is disposable: a lost one is covered by the viewer's own timer.
        } catch (RuntimeException failure) {
            // Not the client's doing (the database, say). Left to escape it would close the socket for a signal that
            // does not matter, so it is logged and dropped like the rest.
            log.warn("Could not relay a typing signal: {}", failure.toString());
        }
    }

    private static UUID userId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(ChatSocketHandshakeInterceptor.USER_ID);
    }
}
