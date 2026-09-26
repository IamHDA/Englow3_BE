package com.englow3.learning.service;

import com.englow3.learning.dto.result.FlashcardStatsResult;

public interface FlashcardStatsService {
    FlashcardStatsResult statsFor(int periodDays);
}
