package com.englow3.tutor.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** One thread of questions and answers, belonging to one learner. */
@Entity
@Table(name = "tutor_conversations")
@Getter
public class TutorConversation {

    /** Matches the column. A longer opening question is cut rather than refused - it is a label, not the message. */
    private static final int MAX_TITLE_LENGTH = 200;

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Column
    private String topic;

    /** Kept so the list can sort by activity without reading a single message. */
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected TutorConversation() {
    }

    /**
     * Opens a thread, titled from the first thing the learner said.
     * <p>
     * The title is derived rather than asked for. Nobody names a conversation before having it, and "Conversation 4" is
     * a list nobody can read.
     */
    public static TutorConversation start(UUID userId, String firstMessage, String topic, Instant now) {
        requireMessage(firstMessage);

        TutorConversation conversation = new TutorConversation();
        conversation.id = UUID.randomUUID();
        conversation.userId = userId;
        conversation.title = titleFrom(firstMessage);
        conversation.topic = topic;
        conversation.lastMessageAt = now;
        conversation.messageCount = 0;
        return conversation;
    }

    /**
     * Counts a turn that has been added.
     * <p>
     * Both the learner's message and the tutor's reply count. The number is what the list shows as the size of the
     * conversation, and a learner who sees "3 messages" over a thread of six would be reading a different conversation
     * from the one they had.
     */
    public void recordTurn(Instant now) {
        requireOpen();
        this.messageCount++;
        this.lastMessageAt = now;
    }

    public void archive(Instant now) {
        if (archivedAt != null) {
            throw new ConflictException("TUTOR_CONVERSATION_ALREADY_ARCHIVED", "This conversation is already archived");
        }
        this.archivedAt = now;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    /** An archived thread is a transcript, not somewhere to keep talking. */
    private void requireOpen() {
        if (isArchived()) {
            throw new ConflictException("TUTOR_CONVERSATION_ARCHIVED",
                    "This conversation has been archived and cannot take new messages");
        }
    }

    static void requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new BadRequestException("TUTOR_MESSAGE_EMPTY", "A message needs something in it");
        }
    }

    /**
     * The first line of the opening message, cut to fit. Cut on a word boundary where there is one nearby, because a
     * title ending mid-word reads as a bug rather than as an abbreviation.
     */
    private static String titleFrom(String firstMessage) {
        String firstLine = firstMessage.strip().lines().findFirst().orElse("").strip();
        if (firstLine.length() <= MAX_TITLE_LENGTH) {
            return firstLine;
        }

        String cut = firstLine.substring(0, MAX_TITLE_LENGTH - 1);
        int lastSpace = cut.lastIndexOf(' ');
        return (lastSpace > MAX_TITLE_LENGTH / 2 ? cut.substring(0, lastSpace) : cut) + "…";
    }
}
