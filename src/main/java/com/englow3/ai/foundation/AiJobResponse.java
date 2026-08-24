package com.englow3.ai.foundation;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record AiJobResponse(UUID id, String jobType, AiCapability capability, AiJobStatus status, JsonNode result,
        String errorCode) {

    static AiJobResponse from(AiJob job) {
        return new AiJobResponse(job.getId(), job.getJobType(), job.getCapability(), job.getStatus(),
                job.getOutputPayload(), job.getErrorCode());
    }
}
