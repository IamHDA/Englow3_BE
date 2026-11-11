package com.englow3.exam.dto.response;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import com.englow3.exam.dto.result.FullTestPartStartResult;

public record FullTestPartStartResponse(UUID attemptId, @Schema(nullable = true) String uploadUrl) {
    public static FullTestPartStartResponse from(FullTestPartStartResult result) {
        return new FullTestPartStartResponse(result.attemptId(), result.uploadUrl());
    }
}
