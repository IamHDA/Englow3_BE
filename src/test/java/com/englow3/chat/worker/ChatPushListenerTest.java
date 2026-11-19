package com.englow3.chat.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.englow3.chat.dto.response.ChatSocketFrame;
import com.englow3.chat.dto.result.ChatMessageResult;
import com.englow3.chat.service.ChatMessageRecalled;
import com.englow3.chat.service.ChatMessageSent;
import com.englow3.chat.service.ChatMessageService;
import com.englow3.chat.service.ChatReadAdvanced;
import com.englow3.chat.service.ChatSocketRegistry;

class ChatPushListenerTest {

    private final ChatMessageService messageService = mock(ChatMessageService.class);
    private final ChatSocketRegistry registry = mock(ChatSocketRegistry.class);
    private final ChatPushListener listener = new ChatPushListener(messageService, registry);

    private final UUID conversationId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID sender = UUID.randomUUID();
    private final UUID recipient = UUID.randomUUID();

    private ChatMessageResult messageFor(UUID viewer) {
        return new ChatMessageResult(messageId, conversationId, 42, sender, "hi", null, false,
                Instant.parse("2026-11-15T10:00:00Z"), viewer.equals(sender) ? "client-1" : null);
    }

    @Test
    void everyListenerRunsAfterTheCommitAndEvenWithoutATransaction() {
        for (String method : new String[] { "onMessageSent", "onMessageRecalled", "onReadAdvanced" }) {
            java.lang.reflect.Method listener = java.util.Arrays.stream(ChatPushListener.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow();
            org.springframework.transaction.event.TransactionalEventListener annotation = org.springframework.core.annotation.AnnotatedElementUtils
                    .findMergedAnnotation(listener,
                            org.springframework.transaction.event.TransactionalEventListener.class);

            assertThat(annotation).as(method).isNotNull();
            assertThat(annotation.phase()).as(method)
                    .isEqualTo(org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT);
            assertThat(annotation.fallbackExecution()).as(method).isTrue();
        }
    }

    @Test
    void aNewMessageIsPushedToTheRecipientAndToTheSendersOtherDevices() {
        when(messageService.forPush(messageId, recipient)).thenReturn(Optional.of(messageFor(recipient)));
        when(messageService.forPush(messageId, sender)).thenReturn(Optional.of(messageFor(sender)));

        listener.onMessageSent(new ChatMessageSent(conversationId, messageId, 42, sender, Set.of(recipient)));

        verify(messageService).forPush(messageId, recipient);
        verify(messageService).forPush(messageId, sender);
        ArgumentCaptor<ChatSocketFrame> toRecipient = ArgumentCaptor.forClass(ChatSocketFrame.class);
        verify(registry, times(1)).sendTo(eq(recipient), toRecipient.capture());
        ArgumentCaptor<ChatSocketFrame> toSender = ArgumentCaptor.forClass(ChatSocketFrame.class);
        verify(registry, times(1)).sendTo(eq(sender), toSender.capture());
        // Each sees the message their own way: the sender's copy carries the clientId, the recipient's does not.
        assertThat(((ChatSocketFrame.Message) toRecipient.getValue()).message().clientId()).isNull();
        assertThat(((ChatSocketFrame.Message) toSender.getValue()).message().clientId()).isEqualTo("client-1");
        assertThat(((ChatSocketFrame.Message) toRecipient.getValue()).message().seq()).isEqualTo(42);
    }

    @Test
    void aMessageThatIsGoneByNowIsNotPushed() {
        when(messageService.forPush(any(), any())).thenReturn(Optional.empty());

        listener.onMessageSent(new ChatMessageSent(conversationId, messageId, 42, sender, Set.of(recipient)));

        verify(registry, never()).sendTo(any(), any());
    }

    @Test
    void aSocketThatFailsDoesNotEscapeOrKeepTheOtherViewerFromTheirPush() {
        when(messageService.forPush(messageId, recipient)).thenReturn(Optional.of(messageFor(recipient)));
        when(messageService.forPush(messageId, sender)).thenReturn(Optional.of(messageFor(sender)));
        doThrow(new IllegalStateException("socket broke")).when(registry).sendTo(eq(recipient), any());

        assertThatCode(() -> listener
                .onMessageSent(new ChatMessageSent(conversationId, messageId, 42, sender, Set.of(recipient))))
                        .doesNotThrowAnyException();

        verify(registry).sendTo(eq(sender), any());
    }

    @Test
    void aLookupThatFailsDoesNotEscape() {
        when(messageService.forPush(any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> listener
                .onMessageSent(new ChatMessageSent(conversationId, messageId, 42, sender, Set.of(recipient))))
                        .doesNotThrowAnyException();
    }

    @Test
    void aRecallIsPushedToTheOtherPersonAndTheRecallersOtherDevices() {
        listener.onMessageRecalled(new ChatMessageRecalled(conversationId, messageId, 42, sender, Set.of(recipient)));

        ChatSocketFrame frame = new ChatSocketFrame.Recalled(conversationId, messageId, 42);
        verify(registry).sendTo(recipient, frame);
        verify(registry).sendTo(sender, frame);
    }

    @Test
    void aRecallWithNobodyElseInTheConversationStillTellsTheRecallersOwnDevices() {
        listener.onMessageRecalled(new ChatMessageRecalled(conversationId, messageId, 42, sender, Set.of()));

        verify(registry).sendTo(sender, new ChatSocketFrame.Recalled(conversationId, messageId, 42));
        verify(registry, times(1)).sendTo(any(), any());
    }

    @Test
    void aReadMarkIsPushedToTheOtherPersonAndTheReadersOtherDevices() {
        UUID reader = recipient;
        listener.onReadAdvanced(new ChatReadAdvanced(conversationId, reader, 42, Set.of(sender)));

        ChatSocketFrame frame = new ChatSocketFrame.Read(conversationId, reader, 42);
        verify(registry).sendTo(sender, frame);
        verify(registry).sendTo(reader, frame);
    }

    @Test
    void aFailingPushOfARecallOrReadDoesNotEscape() {
        doThrow(new IllegalStateException("socket broke")).when(registry).sendTo(any(), any());

        assertThatCode(() -> {
            listener.onMessageRecalled(
                    new ChatMessageRecalled(conversationId, messageId, 42, sender, Set.of(recipient)));
            listener.onReadAdvanced(new ChatReadAdvanced(conversationId, recipient, 42, Set.of(sender)));
        }).doesNotThrowAnyException();
    }
}
