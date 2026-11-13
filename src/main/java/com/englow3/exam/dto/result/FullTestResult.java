package com.englow3.exam.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTest;

public record FullTestResult(UUID id, String title, String description, CertificateVariant certificateVariant,
        UUID examId, UUID writingTask1Id, UUID writingTask2Id, UUID speakingPart1Id, UUID speakingPart2Id,
        UUID speakingPart3Id, ExamStatus status, String reviewNote, Instant publishedAt, long version) {

    public static FullTestResult of(FullTest test) {
        return new FullTestResult(test.getId(), test.getTitle(), test.getDescription(), test.getCertificateVariant(),
                test.getExamId(), test.getWritingTask1Id(), test.getWritingTask2Id(), test.getSpeakingPart1Id(),
                test.getSpeakingPart2Id(), test.getSpeakingPart3Id(), test.getStatus(), test.getReviewNote(),
                test.getPublishedAt(), test.getVersion());
    }
}
