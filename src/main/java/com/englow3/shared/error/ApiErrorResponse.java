package com.englow3.shared.error;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** fieldErrors only exists on validation errors, traceId only inside the scope of a request. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(String code, String message, Map<String, String> fieldErrors, String traceId,
        Instant timestamp) {

    public static ApiErrorResponse of(String code, String message) {
        return of(code, message, null);
    }

    public static ApiErrorResponse of(String code, String message, String traceId) {
        return new ApiErrorResponse(code, message, null, traceId, Instant.now());
    }

    public static ApiErrorResponse withFields(String code, String message, Map<String, String> fieldErrors,
            String traceId) {
        return new ApiErrorResponse(code, message, fieldErrors, traceId, Instant.now());
    }
}
