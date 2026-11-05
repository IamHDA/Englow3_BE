package com.englow3.translation.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.translation.dto.result.TranslationAttemptResult;
import com.englow3.translation.entity.TranslationAttemptStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record TranslationAttemptResponse(UUID id, UUID setId, UUID itemId, String sourceVi, String response,
        TranslationAttemptStatus status, @Schema(nullable = true) Integer score,
        @Schema(nullable = true) Feedback feedback, @Schema(nullable = true) String errorCode,
        @Schema(nullable = true) String referenceEn, @Schema(nullable = true) String notes, Instant createdAt,
        @Schema(nullable = true) Instant gradedAt) {

    public record Feedback(String verdict, Parts parts, String summary, String improved, List<Mistake> errors) {
    }

    public record Parts(int meaning, int grammar, int vocabulary, int naturalness) {
    }

    public record Mistake(String type, String original, String correction, String explanation) {
    }

    public static TranslationAttemptResponse from(TranslationAttemptResult r) {
        var f = r.feedback();
        Feedback feedback = f == null ? null
                : new Feedback(f.verdict(),
                        new Parts(f.parts().meaning(), f.parts().grammar(), f.parts().vocabulary(),
                                f.parts().naturalness()),
                        f.summary(), f.improved(),
                        f.errors().stream()
                                .map(e -> new Mistake(e.type(), e.original(), e.correction(), e.explanation()))
                                .toList());
        return new TranslationAttemptResponse(r.id(), r.setId(), r.itemId(), r.sourceVi(), r.response(), r.status(),
                r.score(), feedback, r.errorCode(), r.referenceEn(), r.notes(), r.createdAt(), r.gradedAt());
    }
}
