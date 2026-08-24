package com.englow3.ai.governance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.englow3.shared.error.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
class AiContentValidator {

    private static final Set<String> LEVELS = Set.of("A1", "A2", "B1", "B2", "C1");
    private static final Set<String> ACCENTS = Set.of("US", "UK", "AU", "CA");
    private final ObjectMapper objectMapper;

    AiContentValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ValidationResult validate(AiGovernanceDtos.ContentType type, String expectedLevel, JsonNode content) {
        String level = expectedLevel.strip().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw invalid("Content has an unsupported CEFR level");
        }
        if (!content.isObject()) {
            throw invalid("Generated content must be a JSON object");
        }
        text(content, "title", 200);
        JsonNode items = content.path("items");
        if (!items.isArray() || items.isEmpty() || items.size() > 50) {
            throw invalid("Generated content must contain between 1 and 50 items");
        }

        Set<String> fingerprints = new HashSet<>();
        Set<String> conceptIds = new HashSet<>();
        for (JsonNode item : items) {
            if (!item.isObject()) {
                throw invalid("Every generated item must be a JSON object");
            }
            String fingerprint = switch (type) {
                case QUIZ -> validateQuiz(item, conceptIds);
                case DICTATION -> validateDictation(item, conceptIds);
                case FLASHCARDS -> validateFlashcard(item, conceptIds);
                case GRAMMAR_LESSON -> validateGrammar(item, conceptIds);
            };
            if (!fingerprints.add(fingerprint)) {
                throw invalid("Generated content contains duplicate items");
            }
        }

        String hash = sha256(canonical(content));
        ObjectNode report = objectMapper.createObjectNode().put("valid", true).put("contentType", type.name())
                .put("level", level).put("itemCount", items.size()).put("contentHash", hash);
        var reportConcepts = report.putArray("conceptIds");
        conceptIds.stream().sorted().forEach(reportConcepts::add);
        return new ValidationResult(hash, items.size(), Set.copyOf(conceptIds), report);
    }

    private String validateQuiz(JsonNode item, Set<String> conceptIds) {
        String question = text(item, "question", 2000);
        JsonNode options = item.path("options");
        if (!options.isArray() || options.size() < 3 || options.size() > 4) {
            throw invalid("Quiz items require three or four options");
        }
        int correct = 0;
        Set<String> optionTexts = new HashSet<>();
        for (JsonNode option : options) {
            String optionText = text(option, "text", 1000);
            text(option, "rationaleVi", 2000);
            if (!option.path("isCorrect").isBoolean()) {
                throw invalid("Every quiz option requires an isCorrect boolean");
            }
            correct += option.path("isCorrect").asBoolean() ? 1 : 0;
            if (!optionTexts.add(normalize(optionText))) {
                throw invalid("Quiz options must be unique");
            }
        }
        if (correct != 1) {
            throw invalid("Quiz items require exactly one correct option");
        }
        text(item, "explanationEn", 4000);
        text(item, "explanationVi", 4000);
        difficulty(item);
        concepts(item, conceptIds);
        return normalize(question);
    }

    private String validateDictation(JsonNode item, Set<String> conceptIds) {
        String script = text(item, "script", 5000);
        String accent = text(item, "accent", 2).toUpperCase(Locale.ROOT);
        if (!ACCENTS.contains(accent)) {
            throw invalid("Dictation accent must be US, UK, AU, or CA");
        }
        JsonNode segments = item.path("segments");
        if (!segments.isArray() || segments.isEmpty() || segments.size() > 100) {
            throw invalid("Dictation requires between 1 and 100 segments");
        }
        int previousEnd = -1;
        StringBuilder combined = new StringBuilder();
        for (JsonNode segment : segments) {
            String segmentText = text(segment, "text", 1000);
            int start = integer(segment, "startMs", 0, Integer.MAX_VALUE);
            int end = integer(segment, "endMs", 1, Integer.MAX_VALUE);
            if (end <= start || start < previousEnd) {
                throw invalid("Dictation segment timestamps must be ordered and non-overlapping");
            }
            previousEnd = end;
            if (!combined.isEmpty()) {
                combined.append(' ');
            }
            combined.append(segmentText);
        }
        if (!normalize(script).equals(normalize(combined.toString()))) {
            throw invalid("Dictation segments must reproduce the full script");
        }
        difficulty(item);
        concepts(item, conceptIds);
        return normalize(script);
    }

    private String validateFlashcard(JsonNode item, Set<String> conceptIds) {
        String lemma = text(item, "lemma", 200);
        String pos = text(item, "pos", 50);
        text(item, "senseLabelEn", 500);
        text(item, "ipaUs", 200);
        text(item, "definitionEn", 2000);
        text(item, "definitionVi", 2000);
        JsonNode examples = item.path("examples");
        if (!examples.isArray() || examples.isEmpty() || examples.size() > 10) {
            throw invalid("Flashcards require between 1 and 10 examples");
        }
        for (JsonNode example : examples) {
            text(example, "sentence", 2000);
            text(example, "translation", 2000);
        }
        textArray(item, "topics", 1, 20);
        difficulty(item);
        concepts(item, conceptIds);
        return normalize(lemma) + '|' + normalize(pos) + '|' + item.path("senseIndex").asInt(1);
    }

    private String validateGrammar(JsonNode item, Set<String> conceptIds) {
        String title = text(item, "titleEn", 500);
        text(item, "titleVi", 500);
        text(item, "theoryVi", 10000);
        text(item, "theoryEnSummary", 5000);
        textArray(item, "formPatterns", 1, 20);
        difficulty(item);
        concepts(item, conceptIds);
        return normalize(title);
    }

    private void concepts(JsonNode item, Set<String> allConceptIds) {
        for (String conceptId : textArray(item, "conceptIds", 1, 20)) {
            if (!conceptId.matches("[a-z0-9][a-z0-9_.-]{1,99}")) {
                throw invalid("Content contains an invalid concept ID");
            }
            allConceptIds.add(conceptId);
        }
    }

    private void difficulty(JsonNode item) {
        JsonNode value = item.path("difficultyPrior");
        if (!value.isNumber()) {
            throw invalid("Content requires a numeric difficultyPrior");
        }
        BigDecimal difficulty = value.decimalValue();
        if (difficulty.compareTo(BigDecimal.ZERO) < 0 || difficulty.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("difficultyPrior must be between 0 and 1");
        }
    }

    private Set<String> textArray(JsonNode source, String field, int minimum, int maximum) {
        JsonNode values = source.path(field);
        if (!values.isArray() || values.size() < minimum || values.size() > maximum) {
            throw invalid(field + " has an invalid item count");
        }
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank() || !result.add(value.asText().strip())) {
                throw invalid(field + " must contain unique non-empty strings");
            }
        }
        return result;
    }

    private String text(JsonNode source, String field, int maximum) {
        JsonNode value = source.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maximum) {
            throw invalid(field + " is missing, blank, or too long");
        }
        return value.asText().strip();
    }

    private int integer(JsonNode source, String field, int minimum, int maximum) {
        JsonNode value = source.path(field);
        if (!value.isIntegralNumber() || value.asLong() < minimum || value.asLong() > maximum) {
            throw invalid(field + " is outside the allowed range");
        }
        return value.asInt();
    }

    private String canonical(JsonNode content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize AI content for validation", ex);
        }
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private BadRequestException invalid(String message) {
        return new BadRequestException("AI_CONTENT_SCHEMA_INVALID", message);
    }

    record ValidationResult(String contentHash, int itemCount, Set<String> conceptIds, JsonNode report) {
    }
}
