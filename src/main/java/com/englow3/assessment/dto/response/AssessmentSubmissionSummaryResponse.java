package com.englow3.assessment.dto.response;

import java.time.Instant;
import java.util.UUID;
import com.englow3.assessment.dto.result.AssessmentSubmissionSummary;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;

public record AssessmentSubmissionSummaryResponse(UUID id, AssessmentSkill skill, AssessmentAttemptStatus status,
        Instant submittedAt, long version, UUID learnerId, String learnerName, Task task) {
    public record Task(UUID id, String title) {
    }

    public static AssessmentSubmissionSummaryResponse from(AssessmentSubmissionSummary result) {
        return new AssessmentSubmissionSummaryResponse(result.id(), result.skill(), result.status(),
                result.submittedAt(), result.version(), result.learnerId(), result.learnerName(),
                new Task(result.task().id(), result.task().title()));
    }
}
