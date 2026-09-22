package com.englow3.ai.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.ai.service.RetryBackoff;
import com.englow3.shared.error.ConflictException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One durable unit of AI work.
 * <p>
 * Durable because the alternative is calling a provider inside the request that asked for it: a learner would hold a
 * connection open for however long the provider takes, a restart would lose the work with no record that it was ever
 * wanted, and a transient 503 would surface as a failure to the person who did nothing wrong. A row here is a promise
 * that the work was accepted and will be retried.
 * <p>
 * No {@code @Setter}: every field a rule guards - status, retry count, timestamps, payload - would get one.
 */
@Entity
@Table(name = "ai_jobs")
@Getter
public class AiJob {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, updatable = false)
    private AiJobType jobType;

    /**
     * What the job is about, as a name and an id rather than a foreign key. The queue is shared infrastructure and must
     * not know which modules exist, let alone hold a relationship into their tables.
     */
    @Column(name = "target_type", nullable = false, updatable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
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

    /** JSON, as text. The queue never reads inside it - only the handler for this job type knows the shape. */
    @Column(name = "input_payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String inputPayload;

    @Column(name = "output_payload", columnDefinition = "jsonb")
    private String outputPayload;

    /**
     * What makes enqueueing safe to repeat. Unique in the schema, so a double submission loses the race at the database
     * rather than producing two charges against the provider.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(name = "retry_count", nullable = false)
    private short retryCount;

    @Column(name = "max_retry_count", nullable = false)
    private short maxRetryCount;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    /** When a PENDING row becomes eligible again. Null means "now". */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected AiJob() {
    }

    public static AiJob pending(AiJobType jobType, String targetType, UUID targetId, String providerName,
            String modelName, String promptVersion, String inputPayload, String idempotencyKey, short maxRetryCount) {
        AiJob job = new AiJob();
        job.id = UUID.randomUUID();
        job.jobType = jobType;
        job.targetType = targetType;
        job.targetId = targetId;
        job.status = AiJobStatus.PENDING;
        job.providerName = providerName;
        job.modelName = modelName;
        job.promptVersion = promptVersion;
        job.inputPayload = inputPayload;
        job.idempotencyKey = idempotencyKey;
        job.retryCount = 0;
        job.maxRetryCount = maxRetryCount;
        return job;
    }

    /**
     * A worker takes the job. Refuses anything not PENDING so two workers that both read the row before either wrote
     * cannot both proceed - the database lock is the first guard, this is the second.
     */
    public void claim(Instant now) {
        if (status != AiJobStatus.PENDING) {
            throw new ConflictException("AI_JOB_NOT_PENDING",
                    "Only a pending job can be claimed; this one is %s".formatted(status));
        }
        this.status = AiJobStatus.RUNNING;
        this.startedAt = now;
    }

    public void succeed(String outputPayload, Instant now) {
        this.status = AiJobStatus.SUCCEEDED;
        this.outputPayload = outputPayload;
        this.completedAt = now;
        this.errorCode = null;
        this.errorMessage = null;
        this.nextRetryAt = null;
    }

    /**
     * Records a failure and decides whether it is the last one.
     *
     * @param retryable
     *            whether the provider said the request could work later. A malformed recording never will, and retrying
     *            it three times wastes ten minutes to reach the same answer - so it fails at once, keeping the retry
     *            budget for outages.
     */
    public void fail(String errorCode, String errorMessage, boolean retryable, Instant now) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;

        if (!retryable || retryCount + 1 >= maxRetryCount) {
            this.status = AiJobStatus.FAILED;
            this.completedAt = now;
            this.nextRetryAt = null;
        } else {
            this.status = AiJobStatus.PENDING;
            this.nextRetryAt = now.plus(RetryBackoff.delayAfter(retryCount));
        }
        this.retryCount = (short) (retryCount + 1);
    }

    /**
     * Hands a stalled job back to the queue. A worker that died mid-flight leaves a RUNNING row nobody will ever
     * finish, so after the lock timeout the row is assumed abandoned rather than left forever.
     * <p>
     * It costs a retry. The work may well have reached the provider before the worker died, and treating a stall as
     * free would let one crash-looping instance replay the same job without limit.
     */
    public void reclaim(Instant now) {
        if (status != AiJobStatus.RUNNING) {
            throw new ConflictException("AI_JOB_NOT_RUNNING",
                    "Only a running job can be reclaimed; this one is %s".formatted(status));
        }
        fail("AI_JOB_STALLED", "Reclaimed after the worker lock expired", true, now);
    }

    /** True once the job will never be attempted again, either way. */
    public boolean finished() {
        return status == AiJobStatus.SUCCEEDED || status == AiJobStatus.FAILED;
    }
}
