package com.englow3.dictation.dto.command;

public record CreateDictationLessonCommand(String slug, String title, String topic, String targetLevel,
        String certificateType) {

    /** General practice, tied to no certificate. */
    public CreateDictationLessonCommand(String slug, String title, String topic, String targetLevel) {
        this(slug, title, topic, targetLevel, null);
    }
}
