package com.englow3.dictation.dto.result;

import java.util.List;

import com.englow3.dictation.query.DictationStatsQuery.DailyAccuracy;
import com.englow3.dictation.query.DictationStatsQuery.DifficultSentence;
import com.englow3.dictation.query.DictationStatsQuery.SessionSummary;
import com.englow3.dictation.helper.MissedWordCounter.MissedWord;

public record DictationStatsResult(int periodDays, long lessonsCompleted, int averageAccuracyPercent,
        long listeningSeconds, long sentencesPractised, int streakDays, List<DailyAccuracy> activity,
        List<MissedWord> missedWords, List<DifficultSentence> difficultSentences, List<SessionSummary> history) {
}
