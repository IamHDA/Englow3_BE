package com.englow3.learning.dto.command;

import java.util.List;
import java.util.UUID;

public record AddDictationSentencesCommand(UUID lessonId, List<NewSentence> sentences) {

    public record NewSentence(String text, String translationVi, String audioObjectKey, int audioDurationSeconds,
            String hintFirstLetters, String hintRevealWord, String hintPartialTranscript) {
    }
}
