package com.englow3.ai.governance;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.englow3.shared.error.ConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Component
class AiContentPublisher {

    private static final String ARRAY_SEPARATOR = "\u001f";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    AiContentPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    ArrayNode publish(AiGovernanceDtos.ContentType type, String level, JsonNode content) {
        ArrayNode entities = objectMapper.createArrayNode();
        try {
            for (JsonNode item : content.path("items")) {
                EntityRef entity = switch (type) {
                    case QUIZ -> publishQuiz(level, item);
                    case DICTATION -> publishDictation(level, item);
                    case FLASHCARDS -> publishFlashcard(level, item);
                    case GRAMMAR_LESSON -> publishGrammar(level, item);
                };
                entities.addObject().put("entityType", entity.type()).put("entityId", entity.id());
            }
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("AI_CONTENT_PUBLICATION_CONFLICT",
                    "Approved content conflicts with an existing domain record");
        }
        return entities;
    }

    void archive(JsonNode entities) {
        for (JsonNode entity : entities) {
            String type = entity.path("entityType").asText();
            String id = entity.path("entityId").asText();
            int updated = switch (type) {
                case "EXAM_ITEM" -> jdbcTemplate.update(
                        "update exam_items set review_status = 'archived', embedding = null where item_id = ?", id);
                case "SHADOWING_CLIP" -> jdbcTemplate.update(
                        "update shadowing_clips set review_status = 'archived', embedding = null where clip_id = ?",
                        id);
                case "FLASHCARD" -> jdbcTemplate
                        .update("update flashcards set review_status = 'archived', embedding = null where id = ?", id);
                case "GRAMMAR_POINT" -> jdbcTemplate.update(
                        "update grammar_points set review_status = 'archived', embedding = null where id = ?", id);
                default -> throw new IllegalStateException("Unknown published AI content entity type");
            };
            if (updated != 1) {
                throw new ConflictException("AI_CONTENT_ARCHIVE_INCOMPLETE",
                        "A published content entity is missing and cannot be archived safely");
            }
        }
    }

    private EntityRef publishQuiz(String level, JsonNode item) {
        String question = item.path("question").asText().strip();
        rejectDuplicate("select count(*) from exam_items where lower(btrim(question_text)) = lower(btrim(?))",
                question);
        String suffix = suffix();
        String groupId = "aig_" + suffix;
        String itemId = "aiq_" + suffix;
        jdbcTemplate.update("insert into exam_groups (group_id, part_number) values (?, 5)", groupId);
        jdbcTemplate.update("""
                insert into exam_items
                    (item_id, group_id, part_number, question_text, question_type, difficulty_prior,
                     explanation_en, explanation_vi, embedding_text, review_status)
                values (?, ?, 5, ?, 'AI_QUIZ', ?, ?, ?, ?, 'human_approved')
                """, itemId, groupId, question, item.path("difficultyPrior").decimalValue(),
                item.path("explanationEn").asText().strip(), item.path("explanationVi").asText().strip(),
                level + " quiz: " + question);
        int index = 0;
        for (JsonNode option : item.path("options")) {
            String label = String.valueOf((char) ('A' + index++));
            jdbcTemplate.update("""
                    insert into exam_item_options (item_id, label, text, is_correct, rationale_vi)
                    values (?, ?, ?, ?, ?)
                    """, itemId, label, option.path("text").asText().strip(), option.path("isCorrect").asBoolean(),
                    option.path("rationaleVi").asText().strip());
        }
        publishConcepts("exam_item_concepts", "item_id", itemId, item.path("conceptIds"));
        return new EntityRef("EXAM_ITEM", itemId);
    }

    private EntityRef publishDictation(String level, JsonNode item) {
        String script = item.path("script").asText().strip();
        rejectDuplicate("select count(*) from shadowing_clips where lower(btrim(script)) = lower(btrim(?))", script);
        String clipId = "aid_" + suffix();
        int duration = item.path("segments").get(item.path("segments").size() - 1).path("endMs").asInt();
        jdbcTemplate.update("""
                insert into shadowing_clips
                    (clip_id, cefr_level, accent, script, duration_ms, review_status)
                values (?, ?, ?, ?, ?, 'human_approved')
                """, clipId, level, item.path("accent").asText().toUpperCase(), script, duration);
        int order = 1;
        for (JsonNode segment : item.path("segments")) {
            jdbcTemplate.update("""
                    insert into shadowing_segments (clip_id, "order", text, start_ms, end_ms)
                    values (?, ?, ?, ?, ?)
                    """, clipId, order++, segment.path("text").asText().strip(), segment.path("startMs").asInt(),
                    segment.path("endMs").asInt());
        }
        publishConcepts("shadowing_clip_concepts", "clip_id", clipId, item.path("conceptIds"));
        return new EntityRef("SHADOWING_CLIP", clipId);
    }

    private EntityRef publishFlashcard(String level, JsonNode item) {
        String lemma = item.path("lemma").asText().strip();
        String pos = item.path("pos").asText().strip();
        int senseIndex = item.path("senseIndex").asInt(1);
        Integer duplicate = jdbcTemplate.queryForObject("""
                select count(*) from flashcards
                where lower(btrim(lemma)) = lower(btrim(?)) and lower(pos) = lower(?) and sense_index = ?
                """, Integer.class, lemma, pos, senseIndex);
        if (duplicate != null && duplicate > 0) {
            throw duplicate();
        }
        String id = "aif_" + suffix();
        String definitionEn = item.path("definitionEn").asText().strip();
        jdbcTemplate.update("""
                insert into flashcards
                    (id, lemma, pos, sense_index, sense_label_en, ipa_us, ipa_verified,
                     definition_en, definition_vi, cefr_level, cefr_source, topics,
                     difficulty_prior, embedding_text, review_status)
                values (?, ?, ?, ?, ?, ?, false, ?, ?, ?, 'llm_estimate',
                        string_to_array(?, ?), ?, ?, 'human_approved')
                """, id, lemma, pos, senseIndex, item.path("senseLabelEn").asText().strip(),
                item.path("ipaUs").asText().strip(), definitionEn, item.path("definitionVi").asText().strip(), level,
                join(item.path("topics")), ARRAY_SEPARATOR, item.path("difficultyPrior").decimalValue(),
                lemma + " (" + pos + "): " + definitionEn);
        int index = 0;
        for (JsonNode example : item.path("examples")) {
            jdbcTemplate.update("""
                    insert into flashcard_examples (flashcard_id, idx, sentence, translation, source)
                    values (?, ?, ?, ?, 'ai_human_approved')
                    """, id, index++, example.path("sentence").asText().strip(),
                    example.path("translation").asText().strip());
        }
        publishConcepts("flashcard_concepts", "flashcard_id", id, item.path("conceptIds"));
        return new EntityRef("FLASHCARD", id);
    }

    private EntityRef publishGrammar(String level, JsonNode item) {
        String titleEn = item.path("titleEn").asText().strip();
        rejectDuplicate("select count(*) from grammar_points where lower(btrim(title_en)) = lower(btrim(?))", titleEn);
        String id = "aigp_" + suffix();
        String summary = item.path("theoryEnSummary").asText().strip();
        jdbcTemplate.update("""
                insert into grammar_points
                    (id, title_en, title_vi, cefr_level, theory_vi, theory_en_summary,
                     form_patterns, embedding_text, review_status)
                values (?, ?, ?, ?, ?, ?, string_to_array(?, ?), ?, 'human_approved')
                """, id, titleEn, item.path("titleVi").asText().strip(), level, item.path("theoryVi").asText().strip(),
                summary, join(item.path("formPatterns")), ARRAY_SEPARATOR, titleEn + ": " + summary);
        publishConcepts("grammar_point_concepts", "grammar_point_id", id, item.path("conceptIds"));
        return new EntityRef("GRAMMAR_POINT", id);
    }

    private void publishConcepts(String table, String idColumn, String id, JsonNode conceptIds) {
        String sql = "insert into " + table + " (" + idColumn + ", concept_id) values (?, ?)";
        for (JsonNode conceptId : conceptIds) {
            jdbcTemplate.update(sql, id, conceptId.asText());
        }
    }

    private void rejectDuplicate(String sql, String value) {
        Integer duplicate = jdbcTemplate.queryForObject(sql, Integer.class, value);
        if (duplicate != null && duplicate > 0) {
            throw duplicate();
        }
    }

    private ConflictException duplicate() {
        return new ConflictException("AI_CONTENT_DUPLICATE", "Approved content duplicates an existing domain record");
    }

    private String join(JsonNode values) {
        StringBuilder result = new StringBuilder();
        for (JsonNode value : values) {
            if (!result.isEmpty()) {
                result.append(ARRAY_SEPARATOR);
            }
            result.append(value.asText().strip());
        }
        return result.toString();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record EntityRef(String type, String id) {
    }
}
