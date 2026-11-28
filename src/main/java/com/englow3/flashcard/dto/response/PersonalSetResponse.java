package com.englow3.flashcard.dto.response;

import java.util.List;
import java.util.UUID;

import com.englow3.flashcard.dto.result.PersonalSetResult;
import com.englow3.flashcard.entity.FlashcardSetVisibility;
import io.swagger.v3.oas.annotations.media.Schema;

/** A learner's own set in the editor. {@code version} goes back with the next save. */
public record PersonalSetResponse(UUID id, String name, String description, String topic,
        @Schema(nullable = true) String targetLevel, FlashcardSetVisibility visibility, long version,
        List<PersonResponse> sharedWith, List<PersonalCardResponse> cards) {

    public record PersonResponse(UUID id, String displayName, @Schema(nullable = true) String avatarUrl) {
    }

    public static PersonalSetResponse from(PersonalSetResult result) {
        return new PersonalSetResponse(result.id(), result.name(), result.description(), result.topic(),
                result.targetLevel(), result.visibility(), result.version(), result.sharedWith().stream()
                        .map(p -> new PersonResponse(p.id(), p.displayName(), p.avatarUrl())).toList(),
                result.cards().stream().map(PersonalCardResponse::from).toList());
    }
}
