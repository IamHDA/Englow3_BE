package com.englow3.learning.service;

import com.englow3.learning.dto.result.DictationStatsResult;
import com.englow3.learning.dto.result.MistakeQueueResult;

public interface DictationStatsService {
    DictationStatsResult statsFor(int periodDays);

    MistakeQueueResult mistakeQueue();
}
