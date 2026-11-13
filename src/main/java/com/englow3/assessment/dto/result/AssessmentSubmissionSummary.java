package com.englow3.assessment.dto.result;

import java.time.Instant;
import java.util.UUID;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;

public record AssessmentSubmissionSummary(UUID id, AssessmentSkill skill, AssessmentAttemptStatus status,
        Instant submittedAt, long version, UUID learnerId, String learnerName, Task task) {
    public record Task(UUID id, String title) {
    }
}
