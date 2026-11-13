package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.result.FullTestSittingResult;
import com.englow3.exam.entity.FullTest;
import com.englow3.exam.entity.FullTestPartGrading;
import com.englow3.exam.entity.FullTestSittingStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record FullTestSittingResponse(UUID id, UUID fullTestId, String title, FullTestSittingStatus status,
        Instant startedAt,
        @Schema(description = "Every part must be submitted by then; grading may finish later") Instant deadlineAt,
        @Schema(description = "A sitting still missing a grade then expires and keeps its provisional band") Instant gradesDueAt,
        @Schema(nullable = true) Instant completedAt,
        @Schema(description = "The Listening + Reading paper to sit in full") UUID examId,
        @Schema(nullable = true) UUID examAttemptId, List<PartResponse> parts,
        @Schema(nullable = true) BigDecimal listeningBand, @Schema(nullable = true) BigDecimal readingBand,
        @Schema(nullable = true) BigDecimal writingBand, @Schema(nullable = true) BigDecimal speakingBand,
        @Schema(nullable = true, description = "Estimate from Listening and Reading until the overall band exists") BigDecimal provisionalBand,
        @Schema(nullable = true) BigDecimal overallBand) {

    public record PartResponse(FullTest.Slot slot, UUID taskId, @Schema(nullable = true) UUID attemptId,
            @Schema(nullable = true, description = "How grading of the attached answer stands; null with no answer, and in lists") FullTestPartGrading grading) {
    }

    public static FullTestSittingResponse from(FullTestSittingResult result) {
        return new FullTestSittingResponse(result.id(), result.fullTestId(), result.title(), result.status(),
                result.startedAt(), result.deadlineAt(), result.gradesDueAt(), result.completedAt(), result.examId(),
                result.examAttemptId(),
                result.parts().stream()
                        .map(part -> new PartResponse(part.slot(), part.taskId(), part.attemptId(), part.grading()))
                        .toList(),
                result.listeningBand(), result.readingBand(), result.writingBand(), result.speakingBand(),
                result.provisionalBand(), result.overallBand());
    }
}
