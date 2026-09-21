package com.englow3.exam.dto.command;

import java.util.List;
import java.util.UUID;

public record SubmitExamAttemptCommand(UUID attemptId, List<SubmittedAnswer> answers) {
    public record SubmittedAnswer(UUID questionId, List<UUID> selectedOptionIds) {
    }
}
