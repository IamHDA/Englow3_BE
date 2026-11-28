package com.englow3.flashcard.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;
import com.englow3.flashcard.entity.FlashcardSetKind;
import com.englow3.flashcard.entity.FlashcardSetVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code owner} is set on a learner's own set only; a catalogue set shows no author. {@code canEdit} is true only for
 * the viewer's own personal set.
 */
public record FlashcardSetResponse(UUID id, String slug, String name, String description, String topic,
        @Schema(nullable = true) String targetLevel, long cardCount, long dueCount, long masteredCount,
        @Schema(nullable = true) Instant lastStudiedAt, FlashcardSetKind kind, FlashcardSetVisibility visibility,
        @Schema(nullable = true) OwnerResponse owner, boolean canEdit) {

    public record OwnerResponse(UUID id, String displayName, @Schema(nullable = true) String avatarUrl) {
    }

    public static FlashcardSetResponse from(FlashcardSetSummaryResult result) {
        var owner = result.owner();
        return new FlashcardSetResponse(result.id(), result.slug(), result.name(), result.description(), result.topic(),
                result.targetLevel(), result.cardCount(), result.dueCount(), result.masteredCount(),
                result.lastStudiedAt(), result.kind(), result.visibility(),
                owner == null ? null : new OwnerResponse(owner.id(), owner.displayName(), owner.avatarUrl()),
                result.canEdit());
    }
}
