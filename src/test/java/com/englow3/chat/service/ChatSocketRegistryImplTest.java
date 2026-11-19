package com.englow3.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.englow3.chat.dto.response.ChatSocketFrame;
import com.englow3.chat.service.impl.ChatSocketRegistryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class ChatSocketRegistryImplTest {

    private final ChatSocketRegistryImpl registry = new ChatSocketRegistryImpl(new ObjectMapper()
            .registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));

    private final UUID user = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    private WebSocketSession socket(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private ChatSocketFrame typing() {
        return new ChatSocketFrame.Typing(conversationId, UUID.randomUUID(), true);
    }

    @org.junit.jupiter.api.BeforeEach
    void limit() {
        ReflectionTestUtils.setField(registry, "maxSessionsPerUser", 5);
    }

    @Test
    void theSixthSocketOfOnePersonClosesTheFirstWithAPolicyViolation() throws Exception {
        WebSocketSession first = socket("s1");
        registry.add(user, first);
        for (int i = 2; i <= 5; i++) {
            registry.add(user, socket("s" + i));
        }
        verify(first, never()).close(any(CloseStatus.class));

        WebSocketSession sixth = socket("s6");
        registry.add(user, sixth);

        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(sixth, never()).close(any(CloseStatus.class));
    }

    @Test
    void theEvictedSocketHearsNothingMoreButTheOthersDo() throws Exception {
        WebSocketSession first = socket("s1");
        registry.add(user, first);
        WebSocketSession last = null;
        for (int i = 2; i <= 6; i++) {
            last = socket("s" + i);
            registry.add(user, last);
        }

        registry.sendTo(user, typing());

        verify(first, never()).sendMessage(any());
        verify(last).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendingToSomeoneWithNoSocketIsNotAnError() {
        assertThatCode(() -> registry.sendTo(UUID.randomUUID(), typing())).doesNotThrowAnyException();
    }

    @Test
    void everySocketOfThePersonGetsTheSameJsonFrame() throws Exception {
        WebSocketSession tab = socket("tab");
        WebSocketSession phone = socket("phone");
        WebSocketSession someoneElse = socket("else");
        registry.add(user, tab);
        registry.add(user, phone);
        registry.add(UUID.randomUUID(), someoneElse);

        registry.sendTo(user, new ChatSocketFrame.Read(conversationId, UUID.randomUUID(), 7));

        ArgumentCaptor<WebSocketMessage<?>> sentToTab = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(tab).sendMessage(sentToTab.capture());
        ArgumentCaptor<WebSocketMessage<?>> sentToPhone = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(phone).sendMessage(sentToPhone.capture());
        verify(someoneElse, never()).sendMessage(any());
        String json = (String) sentToTab.getValue().getPayload();
        assertThat(json).contains("\"type\":\"read\"").contains("\"upToSeq\":7")
                .contains("\"conversationId\":\"" + conversationId + "\"");
        assertThat(sentToPhone.getValue().getPayload()).isEqualTo(json);
    }

    @Test
    void aMessageFrameKeepsTheNamesOfTheRestResponse() throws Exception {
        WebSocketSession tab = socket("tab");
        registry.add(user, tab);
        UUID messageId = UUID.randomUUID();
        com.englow3.chat.dto.response.ChatMessageResponse message = new com.englow3.chat.dto.response.ChatMessageResponse(
                messageId, conversationId, 42, user, "hi", null, false, java.time.Instant.parse("2026-11-15T10:00:00Z"),
                null);

        registry.sendTo(user, new ChatSocketFrame.Message(message));

        ArgumentCaptor<WebSocketMessage<?>> sent = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(tab).sendMessage(sent.capture());
        assertThat((String) sent.getValue().getPayload()).startsWith("{\"type\":\"message\",\"message\":{")
                .contains("\"id\":\"" + messageId + "\"").contains("\"seq\":42").contains("\"body\":\"hi\"")
                .contains("\"imageUrl\":null").contains("\"recalled\":false")
                .contains("\"sentAt\":\"2026-11-15T10:00:00Z\"").contains("\"clientId\":null");
    }

    @Test
    void aSocketThatFailsToTakeAFrameIsClosedAndNeverWrittenToAgain() throws Exception {
        WebSocketSession broken = socket("broken");
        WebSocketSession healthy = socket("healthy");
        doThrow(new IOException("broken pipe")).when(broken).sendMessage(any());
        registry.add(user, broken);
        registry.add(user, healthy);

        assertThatCode(() -> registry.sendTo(user, typing())).doesNotThrowAnyException();
        registry.sendTo(user, typing());

        verify(broken, times(1)).sendMessage(any());
        verify(broken).close(any(CloseStatus.class));
        verify(healthy, times(2)).sendMessage(any());
    }

    @Test
    void aSocketThatHasAlreadyBeenRemovedIsNotWrittenTo() throws Exception {
        WebSocketSession gone = socket("gone");
        registry.add(user, gone);

        registry.remove(user, gone);
        registry.sendTo(user, typing());

        verify(gone, never()).sendMessage(any());
    }

    @Test
    void removingASocketTwiceOrOneThatWasNeverAddedIsHarmless() {
        WebSocketSession stranger = socket("stranger");

        assertThatCode(() -> {
            registry.remove(user, stranger);
            registry.add(user, stranger);
            registry.remove(user, stranger);
            registry.remove(user, stranger);
        }).doesNotThrowAnyException();
    }

    @Test
    void aPingGoesToEverySocketAndAFailingOneIsDropped() throws Exception {
        WebSocketSession one = socket("one");
        WebSocketSession two = socket("two");
        WebSocketSession broken = socket("broken");
        doThrow(new IllegalStateException("closed")).when(broken).sendMessage(any());
        registry.add(user, one);
        registry.add(UUID.randomUUID(), two);
        registry.add(UUID.randomUUID(), broken);

        assertThatCode(registry::pingAll).doesNotThrowAnyException();

        verify(one).sendMessage(any(PingMessage.class));
        verify(two).sendMessage(any(PingMessage.class));
        verify(broken).close(any(CloseStatus.class));
    }
}
