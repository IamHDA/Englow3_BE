package com.englow3.assessment.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.assessment.dto.result.AssessmentAttemptResult;
import com.englow3.assessment.dto.result.AssessmentCapabilities;
import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.assessment.dto.result.AssessmentUploadResult;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;

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
