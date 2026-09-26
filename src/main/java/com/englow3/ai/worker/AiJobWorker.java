package com.englow3.ai.worker;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.service.AiJobWorkerQueue;

/**
 * Drains the queue.
 * <p>
 * Three steps, three transactions, on purpose: claim and commit, call the provider with no transaction open, then
 * record the outcome. Doing it in one would hold a database connection for the length of a network round trip, and a
 * handful of concurrent jobs would exhaust the pool while doing nothing but waiting.
 * <p>
 * Only present when {@code app.ai.enabled} is true. A deployment with no provider credentials should not have a thread
 * waking every two seconds to find nothing it could do anything with.
 */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class AiJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);

    private final AiJobWorkerQueue queue;
    private final Map<String, AiJobHandler> handlers = new HashMap<>();
    private final int batchSize;
    private final Duration lockTimeout;

    public AiJobWorker(AiJobWorkerQueue queue, List<AiJobHandler> handlers,
            @Value("${app.ai.worker.batch-size:5}") int batchSize,
            @Value("${app.ai.worker.lock-timeout:5m}") Duration lockTimeout) {
        this.queue = queue;
        this.batchSize = batchSize;
        this.lockTimeout = lockTimeout;
        handlers.forEach(handler -> this.handlers.put(handler.handles(), handler));
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.fixed-delay:2s}")
    public void drain() {
        List<AiJob> claimed = queue.claimBatch(batchSize);

        for (AiJob job : claimed) {
            AiJobHandler.Outcome outcome = attempt(job);
            if (queue.record(job.getId(), outcome)) {
                gaveUp(job, outcome.errorCode());
            }
        }
    }

    /**
     * Frees jobs whose worker died. Runs on its own slower schedule because a stall is rare and the check scans by
     * status - there is no reason to pay for it every two seconds.
     */
    @Scheduled(fixedDelayString = "${app.ai.worker.reconcile-delay:1m}")
    public void reclaimStalled() {
        List<AiJob> reclaimed = queue.reclaimStalled(lockTimeout);
        if (!reclaimed.isEmpty()) {
            log.warn("Reclaimed {} AI job(s) whose worker stopped responding", reclaimed.size());
        }
        reclaimed.stream().filter(AiJob::finished).forEach(job -> gaveUp(job, "AI_JOB_STALLED"));
    }

    /**
     * Tells the job's module nothing more is coming. Guarded, because this runs after the job is already recorded as
     * failed: a handler that throws here must not undo that, or take the rest of the batch down with it.
     */
    private void gaveUp(AiJob job, String errorCode) {
        AiJobHandler handler = handlers.get(job.getJobType().name());
        if (handler == null) {
            return;
        }
        try {
            handler.onGaveUp(job.getTargetId(), errorCode);
        } catch (RuntimeException failure) {
            log.error("AI job {} gave up, and its handler could not record that", job.getId(), failure);
        }
    }

    /**
     * A handler that throws is treated as a retryable failure. That is the safer reading: an exception is usually a bug
     * or a transport problem rather than a statement about the input, and failing the job outright would throw away
     * work that a fixed deployment would have completed.
     * <p>
     * A job type with no handler registered is the one thing that fails permanently, because no amount of retrying will
     * conjure one up.
     */
    private AiJobHandler.Outcome attempt(AiJob job) {
        AiJobHandler handler = handlers.get(job.getJobType().name());
        if (handler == null) {
            log.error("No handler registered for AI job type {} (job {})", job.getJobType(), job.getId());
            return AiJobHandler.Outcome.permanentFailure("AI_JOB_NO_HANDLER",
                    "No handler is registered for %s".formatted(job.getJobType()));
        }

        try {
            return handler.run(job.getId(), job.getTargetId(), job.getInputPayload());
        } catch (RuntimeException failure) {
            log.error("AI job {} threw while running", job.getId(), failure);
            return AiJobHandler.Outcome.transientFailure("AI_JOB_HANDLER_ERROR", failure.getMessage());
        }
    }
}
