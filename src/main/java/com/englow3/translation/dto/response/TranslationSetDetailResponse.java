package com.englow3.translation.dto.response;

import java.util.List;
import java.util.UUID;

import com.englow3.translation.dto.result.TranslationSetDetailResult;
import com.englow3.translation.entity.TranslationAttemptStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record TranslationSetDetailResponse(TranslationSetSummaryResponse set, List<Item> items) {

    public record Item(UUID id, int orderNo, String sourceVi, @Schema(nullable = true) LatestAttempt latestAttempt) {
    }

    public record LatestAttempt(UUID id, TranslationAttemptStatus status, @Schema(nullable = true) Integer score) {
    }

    public static TranslationSetDetailResponse from(TranslationSetDetailResult r) {
        return new TranslationSetDetailResponse(
                TranslationSetSummaryResponse.from(r.set()), r
                        .items().stream().map(
                                item -> new Item(item.id(), item.orderNo(), item.sourceVi(),
                                        item.latestAttempt() == null ? null
                                                : new LatestAttempt(item.latestAttempt().id(),
                                                        item.latestAttempt().status(), item.latestAttempt().score())))
                        .toList());
    }
}
