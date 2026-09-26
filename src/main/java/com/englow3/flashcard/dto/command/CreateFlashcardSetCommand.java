package com.englow3.flashcard.dto.command;

public record CreateFlashcardSetCommand(String slug, String name, String description, String topic,
        String targetLevel) {
}
