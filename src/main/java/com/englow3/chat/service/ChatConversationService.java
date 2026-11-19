package com.englow3.chat.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.chat.dto.result.ConversationSummaryResult;
import com.englow3.chat.dto.result.OpenConversationOutcome;

/** Conversations and how far each participant has read. The caller is always the signed-in learner. */
public interface ChatConversationService {

    /** The conversation with a friend, created on first use. Refused unless the two are friends right now. */
    OpenConversationOutcome open(UUID friendId);

    /** The caller's conversations that have a message, newest first. {@code canSend} is not worked out here. */
    Page<ConversationSummaryResult> list(Pageable page);

    /** One of the caller's conversations, with {@code canSend}. Not found when the caller is not in it. */
    ConversationSummaryResult get(UUID conversationId);

    /** Moves the caller's read mark up to {@code upToSeq}, never backwards and never past the last message. */
    void markRead(UUID conversationId, long upToSeq);

    /** How many of the caller's conversations hold something unread. */
    long unreadConversationCount();

    /**
     * The other participant, when {@code me} is in the conversation and the two are still friends. Never stores
     * anything, and never refuses: no conversation, not in it, not friends any more - all give empty. For the typing
     * signal, which is disposable.
     */
    Optional<UUID> typingRecipient(UUID me, UUID conversationId);

    /** For the cleanup job: removes conversations nobody is in any more, and their images. Returns how many. */
    int removeEmptyConversations();
}
