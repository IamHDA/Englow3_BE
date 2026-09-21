package com.englow3.learning.dto.command;

public record CreateFlashcardSetCommand(String slug, String name, String description, String topic,
        String targetLevel) {
}
