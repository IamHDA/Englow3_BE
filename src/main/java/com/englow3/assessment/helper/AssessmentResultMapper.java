package com.englow3.assessment.helper;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.englow3.assessment.dto.result.AssessmentAttemptResult;
import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.assessment.entity.AssessmentAttempt;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.storage.ObjectStorageClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AssessmentResultMapper {
    private final ObjectMapper mapper;
    private final ObjectStorageClient storage;
    private final CurrentUser currentUser;
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
        if (!backOffice && a.getStatus() != AssessmentAttemptStatus.COMPLETED) {
            snapshot = snapshot.hideSolution();
        }
        // Staff and admins see every criterion band, including when they practise as a learner to test the flow.
        boolean fullScores = backOffice || currentUser.hasRole("STAFF") || currentUser.hasRole("ADMIN");
        String report = fullScores ? a.getReport() : AssessmentReportRedactor.forLearner(mapper, a.getReport());
        String url = a.getAudioObjectKey() == null ? null
                : storage.presignGet(bucket, a.getAudioObjectKey(), Duration.ofHours(1)).toString();
        return new AssessmentAttemptResult(a.getId(), a.getTaskId(), a.getSkill(), snapshot, a.getStatus(),
                a.getAnswerText(), url, a.getRecognizedText(), report, a.getSource(), a.getErrorCode(),
                AssessmentRubric.wordCount(a.getAnswerText()), a.getVersion(), a.getCreatedAt(), a.getSubmittedAt(),
                a.getAssessedAt(), backOffice ? a.getUserId() : null, backOffice ? learnerName : null);
    }
}
