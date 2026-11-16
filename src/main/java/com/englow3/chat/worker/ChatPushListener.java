package com.englow3.chat.worker;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.englow3.chat.dto.response.ChatMessageResponse;
import com.englow3.chat.dto.response.ChatSocketFrame;
import com.englow3.chat.service.ChatMessageRecalled;
import com.englow3.chat.service.ChatMessageSent;
import com.englow3.chat.service.ChatMessageService;
import com.englow3.chat.service.ChatReadAdvanced;
import com.englow3.chat.service.ChatSocketRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns what chat did into what open sockets are told, once it has committed - a push before the commit could show a
 * message that then rolls back. The person who did it hears too (their other tabs and devices), as well as the other
 * person. A failure here is logged and goes no further: the change is saved and the request that made it succeeded, and
 * whoever missed the push catches up over REST when they reconnect.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatPushListener {

    private final ChatMessageService messageService;
    private final ChatSocketRegistry registry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageSent(ChatMessageSent event) {
        for (UUID viewer : withActor(event.recipientIds(), event.senderId())) {
            // Per viewer: each sees the message their own way (the image link is signed for them, the clientId is
            // only on their own), and one failing must not keep it from the other.
            try {
                messageService.forPush(event.messageId(), viewer).ifPresent(message -> registry.sendTo(viewer,
                        new ChatSocketFrame.Message(ChatMessageResponse.from(message))));
            } catch (RuntimeException failure) {
                log.warn("Could not push chat message {} to a socket: {}", event.messageId(), failure.toString());
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageRecalled(ChatMessageRecalled event) {
        ChatSocketFrame frame = new ChatSocketFrame.Recalled(event.conversationId(), event.messageId(), event.seq());
        pushToAll(withActor(event.recipientIds(), event.actorId()), frame, "recall of message " + event.messageId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReadAdvanced(ChatReadAdvanced event) {
        ChatSocketFrame frame = new ChatSocketFrame.Read(event.conversationId(), event.readerId(), event.upToSeq());
        pushToAll(withActor(event.recipientIds(), event.readerId()), frame,
                "read mark of conversation " + event.conversationId());
    }

    private void pushToAll(Set<UUID> viewers, ChatSocketFrame frame, String what) {
        for (UUID viewer : viewers) {
            try {
                registry.sendTo(viewer, frame);
            } catch (RuntimeException failure) {
                log.warn("Could not push the {} to a socket: {}", what, failure.toString());
            }
        }
    }

    private static Set<UUID> withActor(Set<UUID> recipients, UUID actor) {
        Set<UUID> viewers = new LinkedHashSet<>(recipients);
        viewers.add(actor);
        return viewers;
    }
}
