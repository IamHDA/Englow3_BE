package com.englow3.assessment.dto.result;

import java.util.UUID;
import com.englow3.assessment.entity.*;

public record AssessmentTaskResult(UUID id, AssessmentSkill skill, String title, String taskType, String instructions,
        String rubricNotes, String sampleAnswer, int minimumWords, int timeLimitSeconds, AssessmentTaskStatus status,
        String reviewNote, long version) {
    public static AssessmentTaskResult from(AssessmentTask task, boolean authoring) {
        return new AssessmentTaskResult(task.getId(), task.getSkill(), task.getTitle(), task.getTaskType(),
                task.getInstructions(), authoring ? task.getRubricNotes() : null,
                authoring ? task.getSampleAnswer() : null, task.getMinimumWords(), task.getTimeLimitSeconds(),
                task.getStatus(), authoring ? task.getReviewNote() : null, task.getVersion());
    }

    public AssessmentTaskResult hideSolution() {
        return new AssessmentTaskResult(id, skill, title, taskType, instructions, null, null, minimumWords,
                timeLimitSeconds, status, null, version);
    }
}
