package com.englow3.ai.foundation;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AiJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);

    private final AiJobStateService stateService;
    private final Map<String, AiJobHandler> handlers;
    private final AiProperties properties;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName();

    AiJobWorker(AiJobStateService stateService, List<AiJobHandler> handlers, AiProperties properties) {
        this.stateService = stateService;
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(AiJobHandler::jobType, Function.identity()));
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.ai.worker.fixed-delay:2s}")
    void poll() {
        if (!properties.enabled()) {
            return;
        }
        stateService.recoverStale(Instant.now().minus(properties.worker().lockTimeout()));
        for (int i = 0; i < properties.worker().batchSize(); i++) {
            AiJob job = stateService.claimNext(workerId);
            if (job == null) {
                return;
            }
            execute(job);
        }
    }

    private void execute(AiJob job) {
        AiJobHandler handler = handlers.get(job.getJobType());
        if (handler == null) {
            stateService.fail(job.getId(), "AI_JOB_HANDLER_MISSING", "No worker handles this job type", false);
            return;
        }
        try {
            stateService.succeed(job.getId(), handler.execute(job));
        } catch (AiProviderException ex) {
            log.warn("AI provider failed for job {} with code {}", job.getId(), ex.code());
            stateService.fail(job.getId(), ex.code(), "The AI provider could not complete this job", ex.retryable());
        } catch (RuntimeException ex) {
            log.error("AI job {} failed unexpectedly", job.getId(), ex);
            stateService.fail(job.getId(), "AI_JOB_EXECUTION_FAILED", "The AI job could not be completed", true);
        }
    }
}
