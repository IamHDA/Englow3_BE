package com.englow3.ai.tutor;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "ai_tutor_messages")
@Getter
class TutorMessage {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TutorMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TutorMessageStatus status;

    @Column(nullable = false)
    private String content;

    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "ai_job_id")
    private UUID aiJobId;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Enumerated(EnumType.STRING)
    private TutorMode mode;

    @Column(name = "grounding_required", nullable = false)
    private boolean groundingRequired;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "safety_category", nullable = false)
    private String safetyCategory;

    @Column(name = "refusal_reason")
    private String refusalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TutorMessage() {
    }

    static TutorMessage user(UUID conversationId, String content, String idempotencyKey, TutorMode mode) {
        TutorMessage message = create(conversationId, TutorMessageRole.USER, TutorMessageStatus.COMPLETED, content,
                null);
        message.idempotencyKey = idempotencyKey;
        message.mode = mode;
        return message;
    }

    static TutorMessage pendingAssistant(UUID conversationId, UUID replyToMessageId, TutorMode mode,
            boolean groundingRequired) {
        TutorMessage message = create(conversationId, TutorMessageRole.ASSISTANT, TutorMessageStatus.PENDING, "",
                replyToMessageId);
        message.mode = mode;
        message.groundingRequired = groundingRequired;
        return message;
    }

    private static TutorMessage create(UUID conversationId, TutorMessageRole role, TutorMessageStatus status,
            String content, UUID replyToMessageId) {
        TutorMessage message = new TutorMessage();
        message.id = UUID.randomUUID();
        message.conversationId = conversationId;
        message.role = role;
        message.status = status;
        message.content = content;
        message.replyToMessageId = replyToMessageId;
        message.safetyCategory = "SAFE";
        message.createdAt = Instant.now();
        return message;
    }

    void attachJob(UUID jobId, String promptVersion) {
        aiJobId = jobId;
        this.promptVersion = promptVersion;
    }

    void complete(String content, String modelName, int inputTokens, int outputTokens) {
        this.content = content;
        this.modelName = modelName;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        status = TutorMessageStatus.COMPLETED;
    }

    void refuse(String content, String reason, String safetyCategory) {
        complete(content, "deterministic-guardrail", 0, 0);
        refusalReason = reason;
        this.safetyCategory = safetyCategory;
    }

    void classifySafety(String safetyCategory) {
        this.safetyCategory = safetyCategory;
    }
}
