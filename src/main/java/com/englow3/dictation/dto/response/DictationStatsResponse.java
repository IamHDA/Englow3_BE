package com.englow3.dictation.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.englow3.dictation.dto.result.DictationStatsResult;

public record DictationStatsResponse(int periodDays, long lessonsCompleted, int averageAccuracyPercent,
        long listeningSeconds, long sentencesPractised, int streakDays, List<DailyAccuracyResponse> activity,
        List<MissedWordResponse> missedWords, List<DifficultSentenceResponse> difficultSentences,
        List<SessionSummaryResponse> history) {

    public static DictationStatsResponse from(DictationStatsResult result) {
        return new DictationStatsResponse(result.periodDays(), result.lessonsCompleted(),
                result.averageAccuracyPercent(), result.listeningSeconds(), result.sentencesPractised(),
                result.streakDays(),
                result.activity().stream()
                        .map(day -> new DailyAccuracyResponse(day.day(), day.accuracyPercent(), day.attemptCount()))
                        .toList(),
                result.missedWords().stream()
                        .map(word -> new MissedWordResponse(word.word(), word.missedCount(), word.correctCount(),
                                word.accuracyPercent()))
                        .toList(),
                result.difficultSentences().stream()
                        .map(sentence -> new DifficultSentenceResponse(sentence.sentenceId(), sentence.text(),
                                sentence.topic(), sentence.accuracyPercent(), sentence.attemptCount()))
                        .toList(),
                result.history().stream()
                        .map(session -> new SessionSummaryResponse(session.day(), session.lessonId(),
                                session.lessonTitle(), session.sentenceCount(), session.accuracyPercent(),
                                session.listeningSeconds()))
                        .toList());
    }

    public record DailyAccuracyResponse(LocalDate day, int accuracyPercent, long attemptCount) {
    }

    public record MissedWordResponse(String word, long missedCount, long correctCount, int accuracyPercent) {
    }

    public record DifficultSentenceResponse(UUID sentenceId, String text, String topic, int accuracyPercent,
            long attemptCount) {
    }

    public record SessionSummaryResponse(LocalDate day, UUID lessonId, String lessonTitle, long sentenceCount,
            int accuracyPercent, long listeningSeconds) {
    }
}
