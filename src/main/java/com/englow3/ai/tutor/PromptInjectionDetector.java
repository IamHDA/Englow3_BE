package com.englow3.ai.tutor;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class PromptInjectionDetector {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("\\b(ignore|disregard|override)\\b.{0,40}\\b(instruction|prompt|rule)s?\\b"),
            Pattern.compile("\\b(system|developer)\\s+(prompt|message)\\b"),
            Pattern.compile("\\b(reveal|print|show|expose)\\b.{0,40}\\b(secret|credential|api[ -]?key|prompt)\\b"),
            Pattern.compile("<\\s*/?\\s*(system|assistant|developer)\\b"));

    boolean detected(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }
}
