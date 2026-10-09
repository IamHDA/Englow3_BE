package com.englow3.flashcard.dto.result;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.englow3.flashcard.dto.command.AddFlashcardsCommand;
import com.englow3.flashcard.dto.command.CreateFlashcardSetCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote,
        CreateFlashcardSetCommand metadata, List<AddFlashcardsCommand.NewCard> cards, Map<String, String> media) {
}
