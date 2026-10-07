package com.englow3.exam.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.englow3.exam.dto.result.ExamDraftResult;

public record ExamDraftResponse(List<Answer> answers, long version, Instant savedAt) {
    public record Answer(UUID questionId, List<UUID> selectedOptionIds) {
    }

    public static ExamDraftResponse from(ExamDraftResult r) {
        return new ExamDraftResponse(
                r.answers().stream().map(a -> new Answer(a.questionId(), a.selectedOptionIds())).toList(), r.version(),
                r.savedAt());
    }
}
