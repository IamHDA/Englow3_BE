package com.englow3.speaking.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.speaking.dto.result.SpeakingAttemptResult;
import com.englow3.speaking.entity.SpeakingAttemptStatus;
import com.fasterxml.jackson.annotation.JsonRawValue;

/** Every score stays nullable out to the client - a missing measurement is shown as missing, never as a zero. */
public record SpeakingAttemptResponse(UUID id, UUID speakingPromptId, String promptTitle, String referenceText,
        SpeakingAttemptStatus status, String audioUrl, String recognizedText, BigDecimal accuracyPercent,
        BigDecimal fluencyPercent, BigDecimal completenessPercent, BigDecimal prosodyPercent,
        BigDecimal pronunciationPercent, String errorCode, Instant createdAt, Instant assessedAt,
        List<WordResponse> words) {

    public static SpeakingAttemptResponse from(SpeakingAttemptResult result) {
        return new SpeakingAttemptResponse(result.id(), result.speakingPromptId(), result.promptTitle(),
                result.referenceText(), result.status(), result.audioUrl(), result.recognizedText(),
                result.accuracyPercent(), result.fluencyPercent(), result.completenessPercent(),
                result.prosodyPercent(), result.pronunciationPercent(), result.errorCode(), result.createdAt(),
                result.assessedAt(), result.words().stream().map(WordResponse::from).toList());
    }

    public record WordResponse(int orderNo, String word, BigDecimal accuracyPercent, String errorType, Integer offsetMs,
            Integer durationMs, @JsonRawValue String phonemes) {

        public static WordResponse from(SpeakingAttemptResult.WordResult word) {
            return new WordResponse(word.orderNo(), word.word(), word.accuracyPercent(), word.errorType(),
                    word.offsetMs(), word.durationMs(), word.phonemesJson());
        }
    }
}
