package com.englow3.generation.dto.response;

import com.englow3.generation.dto.result.GenerationAllowanceResult;

public record GenerationAllowanceResponse(int limit, long used, long remaining, boolean inProgress) {

    public static GenerationAllowanceResponse from(GenerationAllowanceResult r) {
        return new GenerationAllowanceResponse(r.limit(), r.used(), r.remaining(), r.inProgress());
    }
}
