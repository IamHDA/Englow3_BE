package com.englow3.flashcard.dto.command;

import java.util.UUID;

public record SaveAuthoringCommand(UUID id, Long version, CreateFlashcardSetCommand metadata,
        java.util.List<AddFlashcardsCommand.NewCard> cards) {
}
