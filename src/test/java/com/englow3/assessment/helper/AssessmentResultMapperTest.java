package com.englow3.assessment.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.assessment.entity.AssessmentAttempt;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTaskPurpose;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.storage.ObjectStorageClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AssessmentResultMapperTest {
    private static final String TASK = """
            {"id":"%s","skill":"WRITING","title":"T","taskType":"ESSAY","instructions":"I","rubricNotes":null,
             "sampleAnswer":null,"minimumWords":0,"timeLimitSeconds":0,"status":"PUBLISHED","reviewNote":null,
             "version":0}
            """.formatted(UUID.randomUUID());
    private static final String REPORT = """
            {"overall":6,"criteria":[{"key":"TASK_RESPONSE","score":6,"feedback":"ok"},
             {"key":"COHERENCE_COHESION","score":6,"feedback":"ok"},{"key":"LEXICAL_RESOURCE","score":6,"feedback":"ok"},
             {"key":"GRAMMATICAL_RANGE","score":6,"feedback":"ok"}],"summary":"s","strengths":["a"],"improvements":["b"]}
            """;

    private String reportSeenBy(String role, boolean backOffice) {
        return seenBy(role, backOffice).report();
    }

    private com.englow3.assessment.dto.result.AssessmentAttemptResult seenBy(String role, boolean backOffice) {
        CurrentUser user = mock(CurrentUser.class);
        when(user.hasRole(role == null ? "NONE" : role)).thenReturn(true);
        var mapper = new AssessmentResultMapper(JsonMapper.builderWithJackson2Defaults().build(),
                mock(ObjectStorageClient.class), user,
                new com.englow3.assessment.helper.TaskImages(
                        mock(com.englow3.shared.storage.PresignedUrlResolver.class),
                        mock(com.englow3.shared.storage.ObjectStorageClient.class), "learning",
                        java.time.Duration.ofHours(3)));
        AssessmentAttempt attempt = AssessmentAttempt.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AssessmentSkill.WRITING, TASK, null, null, null);
        ReflectionTestUtils.setField(attempt, "report", REPORT);
        ReflectionTestUtils.setField(attempt, "status", AssessmentAttemptStatus.COMPLETED);
        return mapper.from(attempt, backOffice);
    }

    @Test
    void aSnapshotStoredBeforeTasksHadAPurposeReadsAsPractice() {
        // TASK has no "purpose": snapshots written before V075 look like this, and the BFF declares the field
        // non-null, so a null here failed the learner's whole history query.
        assertEquals(AssessmentTaskPurpose.PRACTICE, seenBy("LEARNER", false).task().purpose());
    }

    @Test
    void aLearnerDoesNotReceiveCriterionScores() {
        assertFalse(reportSeenBy("LEARNER", false).contains("\"score\""));
    }

    @Test
    void staffAndAdminKeepEveryScoreEvenOnTheLearnerEndpoints() {
        assertTrue(reportSeenBy("STAFF", false).contains("\"score\""));
        assertTrue(reportSeenBy("ADMIN", false).contains("\"score\""));
    }

    @Test
    void theBackOfficeEndpointsAlwaysKeepTheScores() {
        assertTrue(reportSeenBy("LEARNER", true).contains("\"score\""));
    }
}
