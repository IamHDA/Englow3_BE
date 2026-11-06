package com.englow3.exam.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.englow3.exam.dto.result.ExamDraftResult;

public record ExamDraftResponse(List<Answer> answers, long version, Instant savedAt) {
    /**
     * @param textAnswer
     *            what was typed on a TEXT_ENTRY question; null on a choice question
     */
    public record Answer(UUID questionId, List<UUID> selectedOptionIds,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String textAnswer) {
    }

    public static ExamDraftResponse from(ExamDraftResult r) {
        return new ExamDraftResponse(r.answers().stream()
                .map(a -> new Answer(a.questionId(), a.selectedOptionIds(), a.textAnswer())).toList(), r.version(),
                r.savedAt());
    }
}
