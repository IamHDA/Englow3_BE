package com.englow3.ai.foundation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Entity
@Table(name = "ai_jobs")
@Getter
public class AiJob {

    @Id
    private UUID id;

    @Column(name = "requester_user_id")
    private UUID requesterUserId;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiCapability capability;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiJobStatus status;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_payload", columnDefinition = "jsonb")
    private JsonNode outputPayload;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    private short retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private short maxRetryCount;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "estimated_cost", nullable = false)
    private BigDecimal estimatedCost;

    @Version
    @Column(nullable = false)
    private long version;

    protected AiJob() {
    }

    static AiJob pending(UUID requesterUserId, AiCapability capability, String jobType, String targetType,
            UUID targetId, String providerName, String modelName, String promptVersion, JsonNode inputPayload,
            String idempotencyKey, String traceId) {
        AiJob job = new AiJob();
        job.id = UUID.randomUUID();
        job.requesterUserId = requesterUserId;
        job.capability = capability;
        job.jobType = jobType;
        job.targetType = targetType;
        job.targetId = targetId;
        job.status = AiJobStatus.PENDING;
        job.providerName = providerName;
        job.modelName = modelName;
        job.promptVersion = promptVersion;
        job.inputPayload = inputPayload;
        job.idempotencyKey = idempotencyKey;
        job.maxRetryCount = 3;
        job.availableAt = Instant.now();
        job.estimatedCost = BigDecimal.ZERO;
        job.traceId = traceId;
        return job;
    }

    void claim(String workerId, Instant now) {
        status = AiJobStatus.PROCESSING;
        lockedBy = workerId;
        lockedAt = now;
        startedAt = startedAt == null ? now : startedAt;
        errorCode = null;
        errorMessage = null;
    }

    void succeed(JsonNode output, int inputTokens, int outputTokens, BigDecimal estimatedCost, Instant now) {
        status = AiJobStatus.SUCCEEDED;
        outputPayload = output;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCost = estimatedCost;
        completedAt = now;
        clearLock();
    }

    void succeed(JsonNode output, int inputTokens, int outputTokens, Instant now) {
        succeed(output, inputTokens, outputTokens, BigDecimal.ZERO, now);
    }

    void fail(String code, String safeMessage, boolean retryable, Instant now) {
        retryCount++;
        errorCode = code;
        errorMessage = safeMessage;
        if (retryable && retryCount <= maxRetryCount) {
            status = AiJobStatus.RETRY_SCHEDULED;
            Duration delay = Duration.ofSeconds(Math.min(300, 1L << Math.min(retryCount, (short) 8)));
            nextRetryAt = now.plus(delay);
            availableAt = nextRetryAt;
        } else {
            status = AiJobStatus.FAILED;
            completedAt = now;
        }
        clearLock();
    }

    void cancel(Instant now) {
        if (!status.terminal() && status != AiJobStatus.PROCESSING) {
            status = AiJobStatus.CANCELLED;
            completedAt = now;
        }
    }

    private void clearLock() {
        lockedAt = null;
        lockedBy = null;
    }
}
