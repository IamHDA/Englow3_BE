package com.englow3.dictation.service;

import com.englow3.dictation.dto.result.DictationStatsResult;
import com.englow3.dictation.dto.result.MistakeQueueResult;

public interface DictationStatsService {
    DictationStatsResult statsFor(int periodDays);

    MistakeQueueResult mistakeQueue();
}
