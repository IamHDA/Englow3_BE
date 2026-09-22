package com.englow3.speaking.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.speaking.entity.SpeakingAttempt;
import com.englow3.speaking.entity.SpeakingAttemptStatus;
import com.englow3.speaking.entity.SpeakingAttemptWord;
import com.englow3.speaking.entity.SpeakingPrompt;

/**
 * One recording and its score.
 * <p>
 * Every score is nullable, all the way out to the client. The provider returns them that way - prosody is absent unless
 * asked for, a recording of silence has no accuracy - and turning a missing measurement into a zero would tell a
 * learner they scored nothing when nothing was measured.
 *
 * @param referenceText
 *            carried alongside so a result reads on its own, without a second call to fetch what was being said
 * @param words
 *            empty while the assessment is still queued, and in list views where nobody is looking at it yet
 */
public record SpeakingAttemptResult(UUID id, UUID speakingPromptId, String promptTitle, String referenceText,
        SpeakingAttemptStatus status, String audioUrl, String recognizedText, BigDecimal accuracyPercent,
        BigDecimal fluencyPercent, BigDecimal completenessPercent, BigDecimal prosodyPercent,
        BigDecimal pronunciationPercent, String errorCode, Instant createdAt, Instant assessedAt,
        List<WordResult> words) {

    public static SpeakingAttemptResult of(SpeakingAttempt attempt, SpeakingPrompt prompt, String audioUrl,
            List<SpeakingAttemptWord> words) {
        return new SpeakingAttemptResult(attempt.getId(), attempt.getSpeakingPromptId(), prompt.getTitle(),
                prompt.getReferenceText(), attempt.getStatus(), audioUrl, attempt.getRecognizedText(),
                attempt.getAccuracyPercent(), attempt.getFluencyPercent(), attempt.getCompletenessPercent(),
                attempt.getProsodyPercent(), attempt.getPronunciationPercent(), attempt.getErrorCode(),
                attempt.getCreatedAt(), attempt.getAssessedAt(), words.stream().map(WordResult::of).toList());
    }

    /**
     * @param phonemesJson
     *            the per-phoneme breakdown as stored. Passed through as text because nothing between here and the
     *            screen needs to look inside it.
     */
    public record WordResult(int orderNo, String word, BigDecimal accuracyPercent, String errorType, Integer offsetMs,
            Integer durationMs, String phonemesJson) {

        public static WordResult of(SpeakingAttemptWord word) {
            return new WordResult(word.getOrderNo(), word.getWord(), word.getAccuracyPercent(), word.getErrorType(),
                    word.getOffsetMs(), word.getDurationMs(), word.getPhonemes());
        }
    }
}
