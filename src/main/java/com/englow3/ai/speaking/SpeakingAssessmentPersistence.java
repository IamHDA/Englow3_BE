package com.englow3.ai.speaking;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

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
        jdbcTemplate.update("delete from speaking_word_scores where session_id = ?", sessionId);
        jdbcTemplate.update("delete from speaking_assessments where session_id = ?", sessionId);
        jdbcTemplate.update("""
                insert into speaking_assessments
                    (session_id, recognized_text, accuracy_score, fluency_score, completeness_score,
                     prosody_score, pronunciation_score, grammar_feedback, vocabulary_feedback,
                     provider_name, provider_request_id, raw_result)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'azure-speech', ?, ?::jsonb)
                """, sessionId, result.recognizedText(), result.accuracy(), result.fluency(), result.completeness(),
                result.prosody(), result.pronunciation(), grammarFeedback, vocabularyFeedback, result.requestId(),
                result.raw().toString());
        int position = 0;
        for (SpeechAssessmentResult.WordAssessment word : result.words()) {
            jdbcTemplate.update("""
                    insert into speaking_word_scores
                        (session_id, position, word, accuracy_score, error_type, offset_ms, duration_ms)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, sessionId, position++, word.word(), word.accuracy(), word.errorType(), word.offsetMs(),
                    word.durationMs());
        }
        jdbcTemplate.update("""
                update speaking_sessions set status = 'COMPLETED', completed_at = ? where id = ?
                """, Timestamp.from(Instant.now()), sessionId);
    }

    @Transactional
    void markFailed(UUID sessionId) {
        jdbcTemplate.update("update speaking_sessions set status = 'FAILED' where id = ?", sessionId);
    }
}
