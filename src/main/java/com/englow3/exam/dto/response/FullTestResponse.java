package com.englow3.exam.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.dto.result.FullTestResult;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record FullTestResponse(UUID id, String title, String description, CertificateVariant certificateVariant,
        UUID examId, UUID writingTask1Id, UUID writingTask2Id, UUID speakingPart1Id, UUID speakingPart2Id,
        UUID speakingPart3Id, ExamStatus status, @Schema(nullable = true) String reviewNote,
        @Schema(nullable = true) Instant publishedAt, long version) {

    public static FullTestResponse from(FullTestResult result) {
        return new FullTestResponse(result.id(), result.title(), result.description(), result.certificateVariant(),
                result.examId(), result.writingTask1Id(), result.writingTask2Id(), result.speakingPart1Id(),
                result.speakingPart2Id(), result.speakingPart3Id(), result.status(), result.reviewNote(),
                result.publishedAt(), result.version());
    }
}
