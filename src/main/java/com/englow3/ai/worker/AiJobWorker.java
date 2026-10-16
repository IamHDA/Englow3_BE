package com.englow3.ai.worker;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.AiServiceReadiness;
import com.englow3.ai.entity.AiJob;
import com.englow3.ai.service.AiJobWorkerQueue;

import jakarta.annotation.PreDestroy;

/** Two reserved lanes. Claim only work that can start now; external I/O never holds a DB transaction. */
@Component
@ConditionalOnProperty(name = { "app.ai.enabled", "app.ai.worker.enabled" }, havingValue = "true")
public class AiJobWorker implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);
    private final AiJobWorkerQueue queue;
    private final Map<String, AiJobHandler> handlers;
    private final Duration lockTimeout;
    private final Duration maxRun;
    private final AiServiceReadiness readiness;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean tutorBusy = new AtomicBoolean();
    private final AtomicBoolean gradingBusy = new AtomicBoolean();
    private final Map<UUID, Running> running = new ConcurrentHashMap<>();
    private volatile boolean closing;

    private record Running(AiJob job, long started) {
    }

    public AiJobWorker(AiJobWorkerQueue queue, List<AiJobHandler> handlers, Duration lockTimeout, Duration maxRun) {
        this(queue, handlers, lockTimeout, maxRun, null);
    }

    @Autowired
    public AiJobWorker(AiJobWorkerQueue queue, List<AiJobHandler> handlers,
            @Value("${app.ai.worker.lock-timeout:3m}") Duration lockTimeout,
            @Value("${app.ai.worker.max-run:140s}") Duration maxRun, AiServiceReadiness readiness) {
        this.queue = queue;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(AiJobHandler::handles, h -> h));
        this.lockTimeout = lockTimeout;
        this.maxRun = maxRun;
        this.readiness = readiness;
        heartbeat.scheduleWithFixedDelay(this::renew, 10, 10, TimeUnit.SECONDS);
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.fixed-delay:2s}")
    public void drain() {
        dispatch(tutorBusy, List.of("TUTOR_REPLY"));
        dispatch(gradingBusy, List.of("PRODUCTIVE_ASSESSMENT", "SPEECH_ASSESSMENT"));
    }

    private void dispatch(AtomicBoolean busy, List<String> types) {
        if (closing || !busy.compareAndSet(false, true))
            return;
        AiJob claimed = null;
        try {
            List<AiJob> jobs = queue.claimBatch(1, types, lockTimeout);
            if (jobs.isEmpty()) {
                busy.set(false);
                return;
            }
            claimed = jobs.getFirst();
            AiJob job = claimed;
            running.put(job.getId(), new Running(job, System.nanoTime()));
            executor.execute(() -> {
                try {
                    execute(job);
                } finally {
                    running.remove(job.getId());
                    busy.set(false);
                }
            });
        } catch (RuntimeException failure) {
            busy.set(false);
            if (claimed != null) {
                running.remove(claimed.getId());
                queue.record(claimed.getId(), claimed.getStartedAt(),
                        AiJobHandler.Outcome.transientFailure("AI_WORKER_STOPPING", "Worker could not start"));
            }
            log.error("AI dispatch failed for lane {}", types, failure);
        }
    }

    private void execute(AiJob job) {
        long start = System.nanoTime();
        AiJobHandler handler = handlers.get(job.getJobType().name());
        AiJobHandler.Outcome outcome;
        try {
            if (job.getCreatedAt() != null && Duration.between(job.getCreatedAt(), java.time.Instant.now())
                    .compareTo(Duration.ofMinutes(15)) > 0) {
                outcome = AiJobHandler.Outcome.permanentFailure("AI_JOB_EXPIRED",
                        "Processing could not start within its time budget");
            } else if (readiness != null && !readiness.isLive()) {
                queue.defer(job.getId(), job.getStartedAt(), Duration.ofSeconds(10));
                return;
            } else
                outcome = handler == null
                        ? AiJobHandler.Outcome.permanentFailure("AI_JOB_NO_HANDLER", "No handler registered")
                        : handler.run(job.getId(), job.getTargetId(), job.getInputPayload());
            if (Duration.ofNanos(System.nanoTime() - start).compareTo(maxRun) > 0) {
                outcome = AiJobHandler.Outcome.transientFailure("AI_JOB_DEADLINE",
                        "Processing exceeded its time budget");
            }
        } catch (RuntimeException failure) {
            log.error("AI computation failed id={}", job.getId(), failure);
            outcome = AiJobHandler.Outcome.transientFailure("AI_JOB_HANDLER_ERROR", "Processing failed");
        }
        AiJobHandler.Outcome result = outcome;
        try {
            if (queue.record(job.getId(), job.getStartedAt(), result, () -> {
                if (handler != null)
                    handler.complete(job.getTargetId(), job.getInputPayload(), result.outputPayload());
            }))
                gaveUp(job, result.errorCode());
        } catch (RuntimeException failure) {
            // A completion transaction rolled back. Its durable job remains recoverable.
            log.error("AI completion failed id={}", job.getId(), failure);
        } finally {
            log.info("AI attempt finished id={} type={} durationMs={} success={}", job.getId(), job.getJobType(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), result.success());
        }
    }

    private void renew() {
        running.values().forEach(active -> {
            if (Duration.ofNanos(System.nanoTime() - active.started()).compareTo(maxRun) >= 0)
                return;
            try {
                queue.heartbeat(active.job().getId(), active.job().getStartedAt(), lockTimeout);
            } catch (RuntimeException failure) {
                log.warn("AI heartbeat failed id={}", active.job().getId());
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.reconcile-delay:30s}")
    public void reclaimStalled() {
        queue.reclaimStalled(lockTimeout).stream().filter(AiJob::finished)
                .forEach(job -> gaveUp(job, "AI_JOB_STALLED"));
    }

    private void gaveUp(AiJob job, String code) {
        AiJobHandler handler = handlers.get(job.getJobType().name());
        if (handler == null)
            return;
        try {
            handler.onGaveUp(job.getTargetId(), code);
        } catch (RuntimeException failure) {
            log.error("AI terminal notification failed id={}", job.getId(), failure);
        }
    }

    @PreDestroy
    public void close() {
        closing = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(8, TimeUnit.SECONDS))
                executor.shutdownNow();
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            heartbeat.shutdownNow();
        }
    }
}
