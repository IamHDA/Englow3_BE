package com.englow3.flashcard.dto.response;

import java.util.UUID;
import com.englow3.flashcard.dto.command.CreateFlashcardSetCommand;
import com.englow3.flashcard.dto.result.AuthoringResult;

public record AuthoringResponse(UUID id, long version, String status, String reviewNote,
        CreateFlashcardSetCommand metadata,
        java.util.List<com.englow3.flashcard.dto.command.AddFlashcardsCommand.NewCard> cards,
        java.util.Map<String, String> media) {
    public static AuthoringResponse from(AuthoringResult r) {
        return new AuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata(), r.cards(),
                r.media());
    }
}
