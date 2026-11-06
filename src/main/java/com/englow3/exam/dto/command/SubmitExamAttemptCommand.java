package com.englow3.exam.dto.command;

import java.util.List;
import java.util.UUID;

public record SubmitExamAttemptCommand(UUID attemptId, List<SubmittedAnswer> answers) {

    /**
     * One answer. A choice question uses {@code selectedOptionIds}; a TEXT_ENTRY question uses {@code textAnswer} and
     * no option. Also the shape a saved draft is stored in, so a draft written before {@code textAnswer} existed reads
     * back with it null.
     */
    public record SubmittedAnswer(UUID questionId, List<UUID> selectedOptionIds, String textAnswer) {

        public SubmittedAnswer {
            selectedOptionIds = selectedOptionIds == null ? List.of() : List.copyOf(selectedOptionIds);
        }

        public SubmittedAnswer(UUID questionId, List<UUID> selectedOptionIds) {
            this(questionId, selectedOptionIds, null);
        }
    }
}
