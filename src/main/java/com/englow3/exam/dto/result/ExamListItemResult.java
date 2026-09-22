package com.englow3.exam.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

/**
 * No description - it is a text column and the list has no room for it. {@code createdByUserId} is not resolved to a
 * name either: the frontend composes it against the admin user list, which costs less than the first exam -> user edge.
 * **No content counts.** A section or question count reads as "is this ready to publish" and is a bad answer to that: a
 * paper with two hundred questions still fails on a score that does not add up. `publish()` answers it exactly, with a
 * code saying which rule broke - so the counts cost an extra aggregate query per page to tell the admin something
 * misleading.
 * <p>
 * The review note is here despite also being a text column, because the list is where a staff member finds out their
 * paper came back and what to change. Making them open each rejected paper to read one sentence is the kind of saving
 * that costs more than it saves.
 */
public record ExamListItemResult(UUID id, String title, ExamType examType, CertificateType certificateType,
        CertificateVariant certificateVariant, TargetLevel targetLevel, ExamStatus status, int versionNumber,
        UUID createdByUserId, Instant publishedAt, Instant createdAt, Instant submittedForReviewAt, String reviewNote) {

    public static ExamListItemResult of(Exam exam) {
        return new ExamListItemResult(exam.getId(), exam.getTitle(), exam.getExamType(), exam.getCertificateType(),
                exam.getCertificateVariant(), exam.getTargetLevel(), exam.getStatus(), exam.getVersionNumber(),
                exam.getCreatedByUserId(), exam.getPublishedAt(), exam.getCreatedAt(), exam.getSubmittedForReviewAt(),
                exam.getReviewNote());
    }
}
