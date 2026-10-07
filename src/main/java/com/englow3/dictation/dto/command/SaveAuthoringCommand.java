package com.englow3.dictation.dto.command;

import java.util.UUID;

public record SaveAuthoringCommand(UUID id, Long version, CreateDictationLessonCommand metadata,
        java.util.List<Sentence> sentences) {
    public record Sentence(String text, String translationVi, String audioObjectKey, int audioDurationSeconds,
            String hintFirstLetters, String hintRevealWord, String hintPartialTranscript, Integer audioStartMs,
            Integer audioEndMs) {
    }
}
