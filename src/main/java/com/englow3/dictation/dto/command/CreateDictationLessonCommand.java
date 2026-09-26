package com.englow3.dictation.dto.command;

public record CreateDictationLessonCommand(String slug, String title, String topic, String targetLevel) {
}
