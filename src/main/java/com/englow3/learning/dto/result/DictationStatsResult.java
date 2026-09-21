package com.englow3.learning.dto.result;

import java.util.List;

import com.englow3.learning.query.DictationStatsQuery.DailyAccuracy;
import com.englow3.learning.query.DictationStatsQuery.DifficultSentence;
import com.englow3.learning.query.DictationStatsQuery.SessionSummary;
import com.englow3.learning.service.MissedWordCounter.MissedWord;

public record DictationStatsResult(int periodDays, long lessonsCompleted, int averageAccuracyPercent,
        long listeningSeconds, long sentencesPractised, int streakDays, List<DailyAccuracy> activity,
        List<MissedWord> missedWords, List<DifficultSentence> difficultSentences, List<SessionSummary> history) {
}
