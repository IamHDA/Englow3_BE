package com.englow3.dictation.dto.result;

import java.util.List;

public record DictationLessonDetailResult(DictationLessonSummaryResult lesson,
        List<DictationSentenceResult> sentences) {
}
