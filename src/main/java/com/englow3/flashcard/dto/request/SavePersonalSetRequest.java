package com.englow3.flashcard.dto.request;

import java.util.List;
import java.util.UUID;

import com.englow3.flashcard.dto.command.SavePersonalSetCommand;
import com.englow3.flashcard.entity.FlashcardSetVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creating or editing a learner's own set. {@code friendIds} is who a SELECTED_FRIENDS set is shown to and must be
 * empty for any other visibility. {@code cards} is accepted on create only; {@code version} is required on edit.
 */
public record SavePersonalSetRequest(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description,
        @NotBlank @Size(max = 60) String topic,
        @Pattern(regexp = "^(A1|A2|B1|B2|C1|C2)$", message = "Target level must be a CEFR band") String targetLevel,
        @NotNull FlashcardSetVisibility visibility, @Size(max = 200) List<@NotNull UUID> friendIds,
        @Size(max = 500) List<@NotNull @Valid PersonalCardRequest> cards, Long version) {

    public SavePersonalSetCommand toCommand() {
        return new SavePersonalSetCommand(name.trim(), description == null ? null : description.trim(), topic.trim(),
                targetLevel, visibility, friendIds,
                cards == null ? List.of() : cards.stream().map(PersonalCardRequest::toCommand).toList(), version);
    }
}
