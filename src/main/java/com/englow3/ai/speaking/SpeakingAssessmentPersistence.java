package com.englow3.ai.speaking;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SpeakingAssessmentPersistence {

    private final JdbcTemplate jdbcTemplate;

    SpeakingAssessmentPersistence(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    void save(UUID sessionId, SpeechAssessmentResult result, String grammarFeedback, String vocabularyFeedback) {
        UUID userId = jdbcTemplate.queryForObject("select user_id from speaking_sessions where id = ?", UUID.class,
                sessionId);
        jdbcTemplate.update("delete from speaking_word_scores where session_id = ?", sessionId);
        jdbcTemplate.update("delete from speaking_assessments where session_id = ?", sessionId);
        jdbcTemplate.update("""
                insert into speaking_assessments
                    (session_id, recognized_text, accuracy_score, fluency_score, completeness_score,
                     prosody_score, pronunciation_score, grammar_feedback, vocabulary_feedback,
                     provider_name, provider_request_id, raw_result)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, sessionId, result.recognizedText(), result.accuracy(), result.fluency(), result.completeness(),
                result.prosody(), result.pronunciation(), grammarFeedback, vocabularyFeedback, result.provider(),
                result.requestId(), sanitizedEvidence(result));
        int position = 0;
        for (SpeechAssessmentResult.WordAssessment word : result.words()) {
            jdbcTemplate.update("""
                    insert into speaking_word_scores
                        (session_id, position, word, accuracy_score, error_type, offset_ms, duration_ms)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, sessionId, position++, word.word(), word.accuracy(), word.errorType(), word.offsetMs(),
                    word.durationMs());
            int wordPosition = position - 1;
            String wordError = normalizeError(word.errorType(), word.accuracy());
            if (wordError != null) {
                observeError(sessionId, userId, "WORD", normalizeUnit(word.word()), wordError, word.accuracy());
            }
            int phonemePosition = 0;
            for (SpeechAssessmentResult.PhonemeAssessment phoneme : word.phonemes()) {
                jdbcTemplate.update("""
                        insert into speaking_phoneme_scores
                            (session_id, word_position, position, phoneme, accuracy_score)
                        values (?, ?, ?, ?, ?)
                        """, sessionId, wordPosition, phonemePosition++, phoneme.phoneme(), phoneme.accuracy());
                String phonemeError = normalizeError(null, phoneme.accuracy());
                if (phonemeError != null) {
                    observeError(sessionId, userId, "PHONEME", normalizeUnit(phoneme.phoneme()), phonemeError,
                            phoneme.accuracy());
                }
            }
        }
        createRecommendations(sessionId, userId);
        jdbcTemplate.update("""
                update speaking_sessions set status = 'COMPLETED', completed_at = ? where id = ?
                """, Timestamp.from(Instant.now()), sessionId);
    }

    @Transactional
    void markFailed(UUID sessionId) {
        jdbcTemplate.update("update speaking_sessions set status = 'FAILED' where id = ?", sessionId);
    }

    private String sanitizedEvidence(SpeechAssessmentResult result) {
        return "{\"provider\":\"" + escapeJson(result.provider()) + "\",\"wordCount\":" + result.words().size() + "}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String normalizeUnit(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}']", "").strip();
    }

    static String normalizeError(String providerError, Double accuracy) {
        if (providerError != null && !providerError.isBlank() && !providerError.equalsIgnoreCase("None")) {
            return providerError.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        }
        return accuracy != null && accuracy < 80 ? "LOW_ACCURACY" : null;
    }

    private void observeError(UUID sessionId, UUID userId, String unitType, String unit, String errorType,
            Double accuracy) {
        if (unit.isBlank()) {
            return;
        }
        int inserted = jdbcTemplate.update("""
                insert into speaking_error_observations
                    (session_id, unit_type, normalized_unit, error_type, accuracy)
                values (?, ?, ?, ?, ?)
                on conflict do nothing
                """, sessionId, unitType, unit, errorType, accuracy);
        if (inserted == 0) {
            return;
        }
        jdbcTemplate.update("""
                insert into speaking_error_aggregates
                    (user_id, unit_type, normalized_unit, error_type, occurrence_count,
                     average_accuracy, first_seen_at, last_seen_at)
                values (?, ?, ?, ?, 1, ?, now(), now())
                on conflict (user_id, unit_type, normalized_unit, error_type) do update
                set average_accuracy = case
                        when excluded.average_accuracy is null then speaking_error_aggregates.average_accuracy
                        when speaking_error_aggregates.average_accuracy is null then excluded.average_accuracy
                        else (speaking_error_aggregates.average_accuracy * speaking_error_aggregates.occurrence_count
                              + excluded.average_accuracy)
                             / (speaking_error_aggregates.occurrence_count + 1)
                    end,
                    occurrence_count = speaking_error_aggregates.occurrence_count + 1,
                    last_seen_at = now()
                """, userId, unitType, unit, errorType, accuracy);
    }

    private void createRecommendations(UUID sessionId, UUID userId) {
        jdbcTemplate.update("delete from speaking_practice_recommendations where session_id = ?", sessionId);
        jdbcTemplate.update("""
                insert into speaking_practice_recommendations
                    (session_id, position, content_type, content_id, reason)
                select ?, row_number() over (order by match_count desc, clip_id)::int,
                       'SHADOWING_CLIP', clip_id,
                       'Practice a reviewed clip containing recurring pronunciation targets'
                from (
                    select c.clip_id, count(*) match_count
                    from shadowing_clips c
                    join speaking_error_aggregates e
                      on e.user_id = ? and e.unit_type = 'WORD'
                     and lower(c.script) like '%' || e.normalized_unit || '%'
                    where c.review_status = 'human_approved' and c.audio_url is not null
                    group by c.clip_id
                    order by match_count desc, c.clip_id limit 3
                ) ranked
                on conflict do nothing
                """, sessionId, userId);
    }
}
