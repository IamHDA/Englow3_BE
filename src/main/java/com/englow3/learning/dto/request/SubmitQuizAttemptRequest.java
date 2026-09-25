package com.englow3.learning.dto.request;

import java.util.List;
import java.util.UUID;

import com.englow3.learning.dto.command.SubmitQuizAttemptCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitQuizAttemptRequest(@NotNull @Size(max = 200) List<@NotNull @Valid AnswerRequest> answers) {

    /** An empty response is a skipped question, which is a normal answer rather than a missing one. */
    public record AnswerRequest(@NotNull UUID questionId, @Size(max = 2000) String response) {
    }

    public SubmitQuizAttemptCommand toCommand(UUID attemptId) {
        return new SubmitQuizAttemptCommand(attemptId,
                answers.stream().map(answer -> new SubmitQuizAttemptCommand.SubmittedAnswer(answer.questionId(),
                        answer.response() == null ? "" : answer.response())).toList());
    }
}
