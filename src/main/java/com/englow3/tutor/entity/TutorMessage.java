package com.englow3.tutor.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/** One turn. The learner's arrives complete; the tutor's arrives empty and is filled when the provider answers. */
@Entity
@Table(name = "tutor_messages")
@Getter
public class TutorMessage {
    @jakarta.persistence.Column(name = "is_summary", nullable = false)
    private boolean summary;

    public void markSummary() {
        summary = true;
    }

    @Id
    private UUID id;

    @Column(name = "tutor_conversation_id", nullable = false, updatable = false)
    private UUID tutorConversationId;

    @Column(name = "order_no", nullable = false, updatable = false)
    private int orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TutorMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TutorMessageStatus status;

    /** Null while a reply is pending. Not blank - "not answered yet" and "answered with nothing" are different. */
    @Column
    private String content;

    @Column(name = "error_code")
    private String errorCode;

    @Column
    private String model;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "report_note")
    private String reportNote;

    @Column(name = "created_at", insertable = false, updatable = false)
    @org.hibernate.annotations.Generated(event = org.hibernate.generator.EventType.INSERT)
    private Instant createdAt;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(nullable = false)
    private int revision;
    @Column(name = "restart_count", nullable = false)
    private int restartCount;

    @Column
    private String correction;

    @Column(name = "report_status")
    private String reportStatus;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "sources_json", columnDefinition = "jsonb", nullable = false)
    private String sourcesJson = "[]";

    public void attachSources(String json) {
        requirePending("given source context");
        sourcesJson = json;
    }

    public void reviewed(String status, String correction) {
        this.reportStatus = status;
        this.correction = correction;
    }

    @Column(name = "stream_epoch")
    private UUID streamEpoch;

    public UUID startStream(int expectedRevision) {
        if (status != TutorMessageStatus.PENDING || revision != expectedRevision)
            return null;
        streamEpoch = UUID.randomUUID();
        content = "";
        return streamEpoch;
    }

    public boolean partial(int expectedRevision, UUID epoch, String text) {
        if (status != TutorMessageStatus.PENDING || revision != expectedRevision
                || !java.util.Objects.equals(streamEpoch, epoch))
            return false;
        if (text.length() > 200000)
            throw new IllegalArgumentException("Reply too large");
        content = text;
        return true;
    }

    protected TutorMessage() {
    }

    /** What the learner typed. Stored before anything is asked of a provider, so nothing they said can be lost. */
    public static TutorMessage fromLearner(UUID conversationId, int orderNo, String content) {
        TutorConversation.requireMessage(content);

        TutorMessage message = new TutorMessage();
        message.id = UUID.randomUUID();
        message.tutorConversationId = conversationId;
        message.orderNo = orderNo;
        message.role = TutorMessageRole.USER;
        message.status = TutorMessageStatus.READY;
        message.content = content.strip();
        return message;
    }

    /**
     * The tutor's side of the turn, written empty.
     * <p>
     * The row exists before the answer does so the screen has something to show as pending, and so a reply that arrives
     * has somewhere to land that is already in the right place in the transcript.
     */
    public static TutorMessage awaitingReply(UUID conversationId, int orderNo) {
        TutorMessage message = new TutorMessage();
        message.id = UUID.randomUUID();
        message.tutorConversationId = conversationId;
        message.orderNo = orderNo;
        message.role = TutorMessageRole.ASSISTANT;
        message.status = TutorMessageStatus.PENDING;
        return message;
    }

    /** Fills a pending reply. An answer of only whitespace is not an answer and is recorded as a failure. */
    public void answer(String content, String model, String promptVersion, Integer inputTokens, Integer outputTokens,
            Instant now) {
        requirePending("answered");
        if (content == null || content.isBlank()) {
            throw new BadRequestException("TUTOR_REPLY_EMPTY", "The tutor returned nothing to show");
        }

        this.content = content.strip();
        this.model = model;
        this.promptVersion = promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.status = TutorMessageStatus.READY;
        this.answeredAt = now;
        this.errorCode = null;
    }

    /**
     * Gives up on a reply.
     * <p>
     * Only called once no retry is coming. A learner told "that failed" who then watches it succeed has been told
     * something untrue, so a failure that is still being retried leaves this row pending.
     */
    public void fail(String errorCode, Instant now) {
        requirePending("failed");
        this.status = TutorMessageStatus.FAILED;
        this.errorCode = errorCode;
        this.answeredAt = now;
    }

    /**
     * Records that the learner says this answer is wrong or inappropriate.
     * <p>
     * Only an assistant turn can be reported: reporting your own message means nothing, and allowing it would put rows
     * in the review queue that no reviewer can act on.
     */
    public void report(String note, Instant now) {
        if (role != TutorMessageRole.ASSISTANT) {
            throw new ConflictException("TUTOR_MESSAGE_NOT_REPORTABLE", "Only the tutor's own answers can be reported");
        }
        if (status != TutorMessageStatus.READY) {
            throw new ConflictException("TUTOR_MESSAGE_NOT_ANSWERED",
                    "There is no answer here to report; this turn is %s".formatted(status));
        }
        this.reportedAt = now;
        this.reportNote = note == null || note.isBlank() ? null : note.strip();
        this.reportStatus = "OPEN";
    }

    public void restart(boolean regenerate) {
        if (role != TutorMessageRole.ASSISTANT || (regenerate ? status != TutorMessageStatus.READY
                : status != TutorMessageStatus.FAILED && status != TutorMessageStatus.CANCELLED)) {
            throw new ConflictException("TUTOR_REPLY_NOT_RETRYABLE", "This reply cannot be restarted");
        }
        if (restartCount >= 3) {
            throw new ConflictException("TUTOR_RETRY_LIMIT", "Start a new question after three restarts");
        }
        revision++;
        restartCount++;
        streamEpoch = null;
        status = TutorMessageStatus.PENDING;
        content = null;
        model = null;
        errorCode = null;
        inputTokens = null;
        outputTokens = null;
        answeredAt = null;
        reportedAt = null;
        reportNote = null;
        reportStatus = null;
        correction = null;
        sourcesJson = "[]";
    }

    public void cancel(Instant now) {
        requirePending("cancelled");
        status = TutorMessageStatus.CANCELLED;
        answeredAt = now;
        errorCode = null;
    }

    public void selectAnswer(String text, String model, Integer input, Integer output, String sources,
            String correction, Instant now) {
        if (role != TutorMessageRole.ASSISTANT || status == TutorMessageStatus.PENDING)
            throw new ConflictException("TUTOR_HISTORY_UNAVAILABLE", "Wait for this reply to finish");
        revision++;
        content = text;
        status = TutorMessageStatus.READY;
        this.model = model;
        inputTokens = input;
        outputTokens = output;
        sourcesJson = sources;
        this.correction = correction;
        reportStatus = correction == null ? null : "RESOLVED";
        reportedAt = null;
        reportNote = null;
        errorCode = null;
        streamEpoch = null;
        answeredAt = now;
    }

    private void requirePending(String verb) {
        if (status != TutorMessageStatus.PENDING) {
            throw new ConflictException("TUTOR_MESSAGE_NOT_PENDING",
                    "Only a turn still waiting can be %s; this one is %s".formatted(verb, status));
        }
    }
}
