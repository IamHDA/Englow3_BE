package com.englow3.learning.dto.result;

import java.util.List;

public record DictationLessonDetailResult(DictationLessonSummaryResult lesson,
        List<DictationSentenceResult> sentences) {
}
