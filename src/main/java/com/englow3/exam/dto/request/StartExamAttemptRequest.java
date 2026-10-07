package com.englow3.exam.dto.request;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.entity.ExamAttemptMode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Optional body of a start. Without one, the paper is opened as a full attempt - what every client did before. */
public record StartExamAttemptRequest(@Schema(nullable = true) ExamAttemptMode mode,
        @Schema(nullable = true) @Size(max = 50) Set<@NotNull UUID> partIds,
        @Schema(nullable = true) @Min(1) @Max(300) Integer timeLimitMinutes,
        @Schema(nullable = true) StartExamAttemptCommand.OpenAttempt onOpen) {

    public StartExamAttemptCommand toCommand(UUID examId) {
        return new StartExamAttemptCommand(examId, mode == null ? ExamAttemptMode.FULL : mode,
                partIds == null ? Set.of() : new HashSet<>(partIds), timeLimitMinutes, onOpen);
    }
}
