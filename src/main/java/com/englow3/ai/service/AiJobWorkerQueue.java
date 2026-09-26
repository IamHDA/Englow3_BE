package com.englow3.ai.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.entity.AiJob;

public interface AiJobWorkerQueue {
    List<AiJob> claimBatch(int limit);

    boolean record(UUID jobId, AiJobHandler.Outcome outcome);

    /** As {@link #record(UUID, AiJobHandler.Outcome)}, landing only while the job is still under this claim. */
    boolean record(UUID jobId, Instant claimedAt, AiJobHandler.Outcome outcome);

    List<AiJob> reclaimStalled(Duration lockTimeout);
}
