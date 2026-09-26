package com.englow3.ai.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.entity.AiJob;

public interface AiJobWorkerQueue {
    List<AiJob> claimBatch(int limit);

    boolean record(UUID jobId, AiJobHandler.Outcome outcome);

    List<AiJob> reclaimStalled(Duration lockTimeout);
}
