package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;

public record LearnerExamListItemResult(UUID id, String title, String description, ExamType examType,
        CertificateType certificateType, CertificateVariant certificateVariant, TargetLevel targetLevel,
        int durationSeconds, BigDecimal maxRawScore, BigDecimal passScore, long questionCount, ExamStatus status,
        Instant publishedAt, BigDecimal bestScorePercentage, String attemptStatus) {

    /** What the catalogue card says about a paper the learner has never opened. */
    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    public static LearnerExamListItemResult of(Exam exam, long questionCount) {
        return of(exam, questionCount, null, false);
    }

    /**
     * The two per-learner figures were hard-coded to null and NOT_STARTED in the BFF, so a learner who had sat a paper
     * twice was still told they had never opened it. They come from the attempt table now.
     */
    public static LearnerExamListItemResult of(Exam exam, long questionCount, BigDecimal bestScorePercentage,
            boolean hasLiveAttempt) {
        String attemptStatus = hasLiveAttempt ? IN_PROGRESS : bestScorePercentage != null ? COMPLETED : NOT_STARTED;
        return new LearnerExamListItemResult(exam.getId(), exam.getTitle(), exam.getDescription(), exam.getExamType(),
                exam.getCertificateType(), exam.getCertificateVariant(), exam.getTargetLevel(),
                exam.getDurationSeconds(), exam.getMaxRawScore(), exam.getPassScore(), questionCount, exam.getStatus(),
                exam.getPublishedAt(), bestScorePercentage, attemptStatus);
    }
}
