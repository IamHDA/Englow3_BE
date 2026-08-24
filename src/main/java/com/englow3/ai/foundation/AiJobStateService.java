package com.englow3.ai.foundation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;

@Service
class AiJobStateService {

    private final AiJobRepository repository;

    AiJobStateService(AiJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AiJob claimNext(String workerId) {
        return repository.findNextReady(Instant.now()).map(job -> {
            job.claim(workerId, Instant.now());
            return job;
        }).orElse(null);
    }

    @Transactional
    public int recoverStale(Instant staleBefore) {
        return repository.recoverStale(staleBefore, Instant.now());
    }

    @Transactional
    public void succeed(UUID jobId, AiJobExecutionResult result) {
        require(jobId).succeed(result.output(), result.inputTokens(), result.outputTokens(), Instant.now());
    }

    @Transactional
    public void fail(UUID jobId, String code, String safeMessage, boolean retryable) {
        require(jobId).fail(code, safeMessage, retryable, Instant.now());
    }

    private AiJob require(UUID jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("AI_JOB_NOT_FOUND", "AI job was not found"));
    }
}
