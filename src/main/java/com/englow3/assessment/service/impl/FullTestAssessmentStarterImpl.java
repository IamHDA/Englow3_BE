package com.englow3.assessment.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.englow3.assessment.api.FullTestAssessmentStarter;
import com.englow3.assessment.service.AssessmentService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FullTestAssessmentStarterImpl implements FullTestAssessmentStarter {
    private final AssessmentService assessments;

    public Prepared start(UUID taskId, UUID requestKey, String contentType, Long contentLength,
            UUID existingAttemptId) {
        var upload = existingAttemptId == null
                ? assessments.startForFullTest(taskId, requestKey, contentType, contentLength)
                : assessments.resumeUpload(existingAttemptId, contentType, contentLength);
        return new Prepared(upload.attempt().id(), upload.uploadUrl());
    }
}
