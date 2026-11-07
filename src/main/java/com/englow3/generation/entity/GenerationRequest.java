package com.englow3.generation.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * One press of "generate": the steps it runs through, what the model has written, and the drafts it produced.
 * <p>
 * The rules: status only moves forward ({@link GenerationStatus}); steps run in {@link #STEPS} order and a result is
 * accepted only for the step the request is at, so a late or repeated job cannot overwrite later work; a step that the
 * model gets wrong is tried at most {@link #MAX_ATTEMPTS} times, after which the whole request fails with the reason.
 * The JSON columns are data the service fills in; the entity guards when they may change.
 */
@Entity
@Table(name = "generation_requests")
@Getter
public class GenerationRequest extends BasePersistedEntity {

    public static final int MAX_ATTEMPTS = 3;
    public static final int MAX_BRIEF_LENGTH = 1000;

    /** The steps of an IELTS full test, in order. The model writes the first eleven; the rest build and record. */
    public static final List<String> STEPS = List.of("plan", "listening-1", "listening-2", "listening-3", "listening-4",
            "reading-1", "reading-2", "reading-3", "writing-1", "writing-2", "speaking", "assemble", "chart", "audio-1",
            "audio-2", "audio-3", "audio-4", "finish");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private GenerationKind kind;

    @Column(name = "requested_by_user_id", nullable = false, updatable = false)
    private UUID requestedByUserId;

    @Column(updatable = false)
    private String brief;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GenerationStatus status;

    private String step;

    @Column(name = "step_index", nullable = false)
    private int stepIndex;

    @Column(nullable = false)
    private int attempt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String components;

    @JdbcTypeCode(SqlTypes.JSON)
    private String assembly;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_problems")
    private String lastProblems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "retry_context")
    private String retryContext;

    @Column(name = "exam_id")
    private UUID examId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "task_ids")
    private String taskIds;

    @Column(name = "full_test_id")
    private UUID fullTestId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected GenerationRequest() {
    }

    public static GenerationRequest create(GenerationKind kind, UUID requestedByUserId, String brief, Instant now) {
        String trimmed = brief == null || brief.isBlank() ? null : brief.strip();
        if (trimmed != null && trimmed.length() > MAX_BRIEF_LENGTH) {
            throw new BadRequestException("GENERATION_BRIEF_TOO_LONG",
                    "The brief is at most %d characters".formatted(MAX_BRIEF_LENGTH));
        }
        GenerationRequest request = new GenerationRequest();
        request.id = UUID.randomUUID();
        request.kind = kind;
        request.requestedByUserId = requestedByUserId;
        request.brief = trimmed;
        request.status = GenerationStatus.QUEUED;
        request.stepIndex = 0;
        request.step = STEPS.getFirst();
        request.components = "{}";
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    /** Whether a result for {@code step} would be accepted now - false for a finished request or another step. */
    public boolean isAt(String step) {
        return !status.finished() && step.equals(this.step);
    }

    /** The model's accepted component for this step, folded into {@code componentsAfter}; moves to the next step. */
    public void recordWritten(String step, String componentsAfter, Instant now) {
        requireAt(step);
        this.components = componentsAfter;
        advance(now);
    }

    /**
     * The model's attempt broke the rules. Returns whether the step will be tried again; on the last attempt the
     * request fails, naming the step and keeping the problems.
     *
     * @param retryContext
     *            what the next attempt shows the model: its last reply and the problems to fix
     */
    public boolean recordProblems(String step, String problems, String retryContext, Instant now) {
        requireAt(step);
        this.attempt++;
        this.lastProblems = problems;
        this.updatedAt = now;
        if (attempt >= MAX_ATTEMPTS) {
            fail("GENERATION_STEP_FAILED",
                    "The model could not write %s correctly in %d attempts".formatted(step, MAX_ATTEMPTS), now);
            return false;
        }
        this.status = GenerationStatus.RUNNING;
        this.retryContext = retryContext;
        return true;
    }

    /** The drafts exist: the paper, the five tasks in slot order, and what is left to record and attach. */
    public void recordAssembled(UUID examId, String taskIds, String assembly, Instant now) {
        requireAt("assemble");
        this.examId = examId;
        this.taskIds = taskIds;
        this.assembly = assembly;
        advance(now);
    }

    /** A step with nothing to keep - a chart or a recording attached to a draft. */
    public void recordDone(String step, Instant now) {
        requireAt(step);
        advance(now);
    }

    public void complete(UUID fullTestId, Instant now) {
        requireAt("finish");
        this.fullTestId = fullTestId;
        this.status = GenerationStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
        this.retryContext = null;
        this.assembly = null;
    }

    /** Stops the request where it is. A request already finished keeps its outcome. */
    public void fail(String code, String message, Instant now) {
        if (status.finished()) {
            return;
        }
        this.status = GenerationStatus.FAILED;
        this.failureCode = code;
        this.failureMessage = message == null ? null : message.substring(0, Math.min(message.length(), 2000));
        this.completedAt = now;
        this.updatedAt = now;
        this.retryContext = null;
    }

    private void advance(Instant now) {
        this.stepIndex++;
        this.step = STEPS.get(stepIndex);
        this.attempt = 0;
        this.lastProblems = null;
        this.retryContext = null;
        this.status = GenerationStatus.RUNNING;
        this.updatedAt = now;
    }

    private void requireAt(String step) {
        if (!isAt(step)) {
            throw new ConflictException("GENERATION_STEP_MISMATCH",
                    "This request is at %s (%s), not %s".formatted(this.step, status, step));
        }
    }
}
