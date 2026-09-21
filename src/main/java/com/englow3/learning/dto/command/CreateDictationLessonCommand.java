package com.englow3.learning.dto.command;

public record CreateDictationLessonCommand(String slug, String title, String topic, String targetLevel) {
}
