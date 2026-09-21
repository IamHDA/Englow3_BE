package com.englow3.exam.dto.request;

import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.command.SubmitExamAttemptCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitExamAttemptRequest(@NotNull @Size(max = 500) List<@Valid AnswerRequest> answers) {

    public record AnswerRequest(@NotNull UUID questionId,
            @NotNull @Size(max = 20) List<@NotNull UUID> selectedOptionIds) {
    }

    public SubmitExamAttemptCommand toCommand(UUID attemptId) {
        return new SubmitExamAttemptCommand(attemptId, answers.stream().map(
                answer -> new SubmitExamAttemptCommand.SubmittedAnswer(answer.questionId(), answer.selectedOptionIds()))
                .toList());
    }
}
