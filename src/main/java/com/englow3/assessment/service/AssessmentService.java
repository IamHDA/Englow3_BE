package com.englow3.assessment.service;

import java.util.UUID;
import com.englow3.assessment.dto.result.*;
import com.englow3.assessment.entity.*;
import org.springframework.data.domain.*;

public interface AssessmentService {
    AssessmentCapabilities capabilities();

    Page<AssessmentTaskResult> catalog(AssessmentSkill skill, Pageable page);

    AssessmentTaskResult task(UUID id);

    AssessmentUploadResult start(UUID taskId, UUID clientKey, String contentType, Long contentLength);

    AssessmentAttemptResult saveDraft(UUID id, String text, long version);

    AssessmentAttemptResult submit(UUID id);

    AssessmentAttemptResult retry(UUID id);

    AssessmentAttemptResult requestReview(UUID id);

    AssessmentAttemptResult attempt(UUID id);

    Page<AssessmentAttemptResult> history(UUID taskId, Pageable page);

    Page<AssessmentAttemptResult> history(UUID taskId, AssessmentSkill skill, AssessmentAttemptStatus status,
            String title, Pageable page);
}
