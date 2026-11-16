package com.englow3.chat.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.englow3.chat.dto.command.SendMessageCommand;
import com.englow3.chat.dto.result.ChatMessageResult;
import com.englow3.chat.dto.result.SendMessageOutcome;

/** Sending, reading back and recalling messages. The caller is always the signed-in learner. */
public interface ChatMessageService {

    /** Sends once per {@code clientId}: a retry returns the first message and sends nothing. */
    SendMessageOutcome send(SendMessageCommand command);

    /**
     * A page of one conversation in ascending seq order: the messages after {@code afterSeq} (catching up), the ones
     * before {@code beforeSeq} (scrolling back), or the newest when neither is given. Both at once is refused.
     */
    List<ChatMessageResult> history(UUID conversationId, Long afterSeq, Long beforeSeq, int limit);

    /** Takes back the caller's own message, once, together with its image. */
    ChatMessageResult recall(UUID messageId);

    /**
     * The message as {@code viewerId} sees it, for pushing it down their socket: the image URL signed for them, the
     * {@code clientId} only when they sent it. Empty when the message is gone or they are not in its conversation.
     */
    Optional<ChatMessageResult> forPush(UUID messageId, UUID viewerId);
}
