package com.englow3.assessment.helper;

import java.time.Duration;
import com.englow3.assessment.dto.result.*;
import com.englow3.assessment.entity.*;
import com.englow3.shared.storage.ObjectStorageClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AssessmentResultMapper {
    private final ObjectMapper mapper;
    private final ObjectStorageClient storage;
    @Value("${app.storage.speaking-bucket:speaking}")
    private String bucket;

    public AssessmentAttemptResult from(AssessmentAttempt a, boolean backOffice) {
        return from(a, backOffice, null);
    }

    public AssessmentAttemptResult from(AssessmentAttempt a, boolean backOffice, String learnerName) {
        AssessmentTaskResult snapshot;
        try {
            snapshot = mapper.readValue(a.getTaskSnapshot(), AssessmentTaskResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored task snapshot is invalid");
        }
        if (!backOffice && a.getStatus() != AssessmentAttemptStatus.COMPLETED)
            snapshot = snapshot.hideSolution();
        String url = a.getAudioObjectKey() == null ? null
                : storage.presignGet(bucket, a.getAudioObjectKey(), Duration.ofHours(1)).toString();
        return new AssessmentAttemptResult(a.getId(), a.getTaskId(), a.getSkill(), snapshot, a.getStatus(),
                a.getAnswerText(), url, a.getRecognizedText(), a.getReport(), a.getSource(), a.getErrorCode(),
                AssessmentRubric.wordCount(a.getAnswerText()), a.getVersion(), a.getCreatedAt(), a.getSubmittedAt(),
                a.getAssessedAt(), backOffice ? a.getUserId() : null, backOffice ? learnerName : null);
    }
}
