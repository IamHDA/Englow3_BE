package com.englow3.ai.foundation;

import java.time.Instant;
import java.util.UUID;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
class AiJobStateService {

    private final AiJobRepository repository;
    private final AiJobEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    AiJobStateService(AiJobRepository repository, AiJobEventPublisher eventPublisher, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public AiJob claimNext(String workerId) {
        return repository.findNextReady(Instant.now()).map(job -> {
            Instant now = Instant.now();
            job.claim(workerId, now);
            eventPublisher.record(job, "PROCESSING");
            Timer.builder("englow3.ai.job.queue.delay").tags(tags(job, "claimed")).register(meterRegistry)
                    .record(Duration.between(job.getCreatedAt(), now));
            return job;
        }).orElse(null);
    }

    @Transactional
    public int recoverStale(Instant staleBefore) {
        var stale = repository.findStaleProcessing(staleBefore);
        stale.forEach(job -> {
            job.fail("AI_WORKER_LOCK_EXPIRED", "The previous worker stopped before completing this job", true,
                    Instant.now());
            eventPublisher.record(job, job.getStatus().name());
            recordOutcome(job, "worker_lock_expired");
        });
        return stale.size();
    }

    @Transactional
    public void succeed(UUID jobId, AiJobExecutionResult result) {
        AiJob job = require(jobId);
        job.succeed(result.output(), result.inputTokens(), result.outputTokens(), result.estimatedCost(),
                Instant.now());
        eventPublisher.record(job, "SUCCEEDED");
        recordOutcome(job, "succeeded");
        String[] tags = tags(job, "succeeded");
        DistributionSummary.builder("englow3.ai.job.input.tokens").tags(tags).register(meterRegistry)
                .record(result.inputTokens());
        DistributionSummary.builder("englow3.ai.job.output.tokens").tags(tags).register(meterRegistry)
                .record(result.outputTokens());
        DistributionSummary.builder("englow3.ai.job.estimated.cost").baseUnit("currency_units").tags(tags)
                .register(meterRegistry).record(result.estimatedCost().doubleValue());
    }

    @Transactional
    public AiJobStatus fail(UUID jobId, String code, String safeMessage, boolean retryable) {
        AiJob job = require(jobId);
        job.fail(code, safeMessage, retryable, Instant.now());
        eventPublisher.record(job, job.getStatus().name());
        recordOutcome(job, job.getStatus() == AiJobStatus.RETRY_SCHEDULED ? "retry" : "failed");
        return job.getStatus();
    }

    private void recordOutcome(AiJob job, String outcome) {
        String[] tags = tags(job, outcome);
        Counter.builder("englow3.ai.job.transitions").tags(tags).register(meterRegistry).increment();
        DistributionSummary.builder("englow3.ai.job.retry.count").tags(tags).register(meterRegistry)
                .record(job.getRetryCount());
        if (job.getStartedAt() != null && job.getStatus().terminal()) {
            Timer.builder("englow3.ai.job.execution").tags(tags).register(meterRegistry)
                    .record(Duration.between(job.getStartedAt(), Instant.now()));
        }
    }

    private String[] tags(AiJob job, String outcome) {
        return new String[] { "capability", job.getCapability().name(), "provider", job.getProviderName(), "model",
                job.getModelName(), "outcome", outcome };
    }

    private AiJob require(UUID jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("AI_JOB_NOT_FOUND", "AI job was not found"));
    }
}
