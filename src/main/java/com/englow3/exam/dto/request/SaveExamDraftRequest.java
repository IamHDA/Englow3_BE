package com.englow3.exam.dto.request;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.englow3.exam.dto.command.SaveExamDraftCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.request.SubmitExamAttemptRequest.AnswerRequest;

public record SaveExamDraftRequest(@PositiveOrZero long version,
        @NotNull @Size(max = 500) List<@NotNull @Valid AnswerRequest> answers) {
    public SaveExamDraftCommand toCommand(UUID id) {
        return new SaveExamDraftCommand(id, version,
                answers.stream().map(a -> new SubmittedAnswer(a.questionId(), a.selectedOptionIds())).toList());
    }
}
