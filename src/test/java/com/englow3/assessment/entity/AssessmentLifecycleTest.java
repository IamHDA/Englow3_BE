package com.englow3.assessment.entity;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.shared.error.*;

class AssessmentLifecycleTest {
    private AssessmentTask task() {
        return AssessmentTask.draft(new AssessmentTaskCommand(AssessmentSkill.WRITING, "Opinion", "TASK_2",
                "Discuss this topic", "Rubric", "Sample", 250, 2400), UUID.randomUUID());
    }

    private AssessmentAttempt attempt() {
        return AssessmentAttempt.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AssessmentSkill.WRITING, "{} ", null, null, null);
    }

    @Test
    void draftCannotSkipReview() {
        assertThrows(ConflictException.class, () -> task().approve(UUID.randomUUID(), Instant.now()));
    }

    @Test
    void rejectedTaskCanBeEditedAndResubmitted() {
        var t = task();
        t.submit();
        t.reject(UUID.randomUUID(), "Explain the data");
        t.edit(new AssessmentTaskCommand(AssessmentSkill.WRITING, "New title", "TASK_1", "Data description", "", "",
                150, 1200));
        t.submit();
        t.approve(UUID.randomUUID(), Instant.now());
        assertEquals(AssessmentTaskStatus.PUBLISHED, t.getStatus());
        assertNull(t.getReviewNote());
    }

    @Test
    void publishedTaskCannotChangeHistoricalQuestion() {
        var t = task();
        t.submit();
        t.approve(UUID.randomUUID(), Instant.now());
        assertThrows(ConflictException.class, () -> t.edit(
                new AssessmentTaskCommand(AssessmentSkill.WRITING, "Changed", "TASK_2", "Changed", "", "", 250, 2400)));
    }

    @Test
    void refusesInvalidSkillTaskType() {
        assertThrows(BadRequestException.class, () -> AssessmentTask.draft(
                new AssessmentTaskCommand(AssessmentSkill.WRITING, "Title", "PART_2", "Instructions", "", "", 0, 120),
                UUID.randomUUID()));
    }

    @Test
    void submittedTextCannotBeEdited() {
        var a = attempt();
        a.saveAnswer("My essay", 0);
        a.submit(false, Instant.now());
        assertThrows(ConflictException.class, () -> a.saveAnswer("Changed", 0));
    }

    @Test
    void concurrentDraftVersionIsRejected() {
        assertThrows(ConflictException.class, () -> attempt().saveAnswer("text", 1));
    }

    @Test
    void rejectsOldAiResultsAfterRetry() {
        var a = attempt();
        a.submit(true, Instant.now());
        a.fail("OUTAGE");
        a.retry(true);
        assertFalse(a.finishAi(1, "old", "old", Instant.now()));
        assertTrue(a.finishAi(2, "new", "new", Instant.now()));
        assertEquals("new", a.getReport());
    }

    @Test
    void humanFallbackCanFinishWithoutAi() {
        var a = attempt();
        a.submit(false, Instant.now());
        a.finishHuman("report", null, Instant.now(), false);
        assertEquals("HUMAN", a.getSource());
        assertEquals(AssessmentAttemptStatus.COMPLETED, a.getStatus());
    }

    @Test
    void onlyAdminCanOverrideCompletedGrade() {
        var a = attempt();
        a.submit(false, Instant.now());
        a.finishHuman("report", null, Instant.now(), false);
        assertThrows(ConflictException.class, () -> a.finishHuman("new", null, Instant.now(), false));
        a.finishHuman("new", null, Instant.now(), true);
        assertEquals("new", a.getReport());
    }
}
