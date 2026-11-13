package com.englow3.translation.dto.response;

import java.util.UUID;

import com.englow3.translation.dto.result.TranslationSetSummaryResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record TranslationSetSummaryResponse(UUID id, String slug, String title, String topic,
        @Schema(nullable = true) String targetLevel, @Schema(nullable = true) String description, long itemCount,
        long gradedCount) {

    public static TranslationSetSummaryResponse from(TranslationSetSummaryResult r) {
        return new TranslationSetSummaryResponse(r.id(), r.slug(), r.title(), r.topic(), r.targetLevel(),
                r.description(), r.itemCount(), r.gradedCount());
    }
}
