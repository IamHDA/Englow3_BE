package com.englow3.flashcard.dto.result;

import java.util.UUID;
import com.englow3.flashcard.dto.command.CreateFlashcardSetCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote,
        CreateFlashcardSetCommand metadata,
        java.util.List<com.englow3.flashcard.dto.command.AddFlashcardsCommand.NewCard> cards,
        java.util.Map<String, String> media) {
}
