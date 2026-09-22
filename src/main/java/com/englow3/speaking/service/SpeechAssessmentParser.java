package com.englow3.speaking.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns what {@code ai_service} answered into the numbers a learner sees.
 * <p>
 * Defensive on purpose. Every score in the adapter's own schema is optional, because the provider omits them: prosody
 * is absent unless asked for, and a recording of silence comes back with no accuracy at all. Reading a missing score as
 * zero would tell a learner they scored nothing when nothing was measured, so missing stays missing all the way to the
 * screen.
 * <p>
 * Kept out of the handler so it can be exercised against real provider payloads without a queue, a database or a
 * network.
 */
public final class SpeechAssessmentParser {

    private SpeechAssessmentParser() {
    }

    public record Phoneme(String phoneme, BigDecimal accuracy) {
    }

    public record Word(String word, BigDecimal accuracy, String errorType, Integer offsetMs, Integer durationMs,
            List<Phoneme> phonemes) {
    }

    /**
     * @param recognizedText
     *            what the provider heard. The one field that must be there - an assessment with nothing recognised is
     *            not an assessment.
     */
    public record Assessment(String recognizedText, BigDecimal accuracy, BigDecimal fluency, BigDecimal completeness,
            BigDecimal prosody, BigDecimal pronunciation, List<Word> words) {
    }

    public static Assessment parse(ObjectMapper mapper, String json) {
        JsonNode root = read(mapper, json);

        String recognizedText = text(root, "recognized_text");
        if (recognizedText == null || recognizedText.isBlank()) {
            throw new BadRequestException("SPEECH_ASSESSMENT_EMPTY", "The assessment recognised no speech");
        }

        return new Assessment(recognizedText, decimal(root, "accuracy"), decimal(root, "fluency"),
                decimal(root, "completeness"), decimal(root, "prosody"), decimal(root, "pronunciation"),
                words(root.path("words")));
    }

    private static JsonNode read(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException malformed) {
            // The adapter answered 200 with something that is not its own schema. Retrying is pointless: it will
            // answer the same way, so this reads as a permanent failure rather than a blip.
            throw new BadRequestException("SPEECH_ASSESSMENT_UNREADABLE",
                    "ai_service returned a body that is not valid JSON");
        }
    }

    private static List<Word> words(JsonNode node) {
        List<Word> words = new ArrayList<>();
        if (!node.isArray()) {
            return words;
        }

        for (JsonNode item : node) {
            String word = text(item, "word");
            if (word == null) {
                // A word entry with no word is not something to store a row for; skipping it loses nothing and
                // keeps one malformed element from failing an otherwise good assessment.
                continue;
            }
            words.add(new Word(word, decimal(item, "accuracy"), text(item, "error_type"), integer(item, "offset_ms"),
                    integer(item, "duration_ms"), phonemes(item.path("phonemes"))));
        }
        return words;
    }

    private static List<Phoneme> phonemes(JsonNode node) {
        List<Phoneme> phonemes = new ArrayList<>();
        if (!node.isArray()) {
            return phonemes;
        }

        for (JsonNode item : node) {
            String symbol = text(item, "phoneme");
            if (symbol != null) {
                phonemes.add(new Phoneme(symbol, decimal(item, "accuracy")));
            }
        }
        return phonemes;
    }

    /** Null for absent, for JSON null, and for a value that is not a number - all three mean "not measured". */
    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
