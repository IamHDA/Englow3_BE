package com.englow3.flashcard.dto.command;

import java.util.List;
import java.util.UUID;

public record SaveAuthoringCommand(UUID id, Long version, CreateFlashcardSetCommand metadata,
        List<AddFlashcardsCommand.NewCard> cards) {
}
