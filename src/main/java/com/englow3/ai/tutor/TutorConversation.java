package com.englow3.ai.tutor;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Entity
@Table(name = "ai_tutor_conversations")
@Getter
class TutorConversation {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TutorConversationStatus status;

    private String summary;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TutorConversation() {
    }

    static TutorConversation start(UUID userId, String title) {
        TutorConversation conversation = new TutorConversation();
        conversation.id = UUID.randomUUID();
        conversation.userId = userId;
        conversation.title = title;
        conversation.status = TutorConversationStatus.ACTIVE;
        conversation.createdAt = Instant.now();
        conversation.updatedAt = conversation.createdAt;
        return conversation;
    }

    void archive() {
        status = TutorConversationStatus.ARCHIVED;
    }

    boolean active() {
        return status == TutorConversationStatus.ACTIVE;
    }
}
