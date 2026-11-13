package com.englow3.flashcard.service;

import com.englow3.flashcard.dto.result.FlashcardStatsResult;

public interface FlashcardStatsService {
    FlashcardStatsResult statsFor(int periodDays);
}
