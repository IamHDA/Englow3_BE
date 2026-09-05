package com.englow3.ai.foundation;

import java.util.UUID;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record AiJobResponse(UUID id, String jobType, AiCapability capability, AiJobStatus status, JsonNode result,
        String errorCode, String targetType, UUID targetId, String provider, String model, short retryCount,
        Instant createdAt, Instant startedAt, Instant completedAt) {

    static AiJobResponse from(AiJob job) {
        return new AiJobResponse(job.getId(), job.getJobType(), job.getCapability(), job.getStatus(),
                job.getOutputPayload(), job.getErrorCode(), job.getTargetType(), job.getTargetId(),
                job.getProviderName(), job.getModelName(), job.getRetryCount(), job.getCreatedAt(), job.getStartedAt(),
                job.getCompletedAt());
    }
}
