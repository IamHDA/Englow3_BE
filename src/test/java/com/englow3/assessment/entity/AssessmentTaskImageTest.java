package com.englow3.assessment.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

/** Only a Writing Task 1 has a chart, and only while it can still be edited (D-IELTS-9). */
class AssessmentTaskImageTest {

    private static AssessmentTask writing(String taskType) {
        return AssessmentTask.draft(new AssessmentTaskCommand(AssessmentSkill.WRITING, "Chart", taskType,
                "Describe the chart", "2010: 40%, 2020: 65%", "", 150, 1200, null, null, "IELTS"), UUID.randomUUID());
    }

    @Test
    void aDraftTaskOneTakesAndDropsItsChart() {
        AssessmentTask task = writing("TASK_1");

        task.attachImage("assessment-tasks/x/chart.png");
        assertThat(task.getImageObjectKey()).isEqualTo("assessment-tasks/x/chart.png");

        task.removeImage();
        assertThat(task.getImageObjectKey()).isNull();
    }

    @Test
    void aTaskTwoEssayHasNoChart() {
        assertThatThrownBy(() -> writing("TASK_2").attachImage("k")).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("TASK_IMAGE_NOT_ALLOWED");
    }

    @Test
    void aSpeakingPartHasNoChart() {
        AssessmentTask speaking = AssessmentTask.draft(new AssessmentTaskCommand(AssessmentSkill.SPEAKING, "Part 2",
                "PART_2", "Describe a place", "", "", 0, 180, null, null, "IELTS"), UUID.randomUUID());

        assertThatThrownBy(() -> speaking.attachImage("k")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void aPublishedTaskKeepsTheChartItWasPublishedWith() {
        AssessmentTask task = writing("TASK_1");
        task.attachImage("first.png");
        task.submit();
        task.approve(UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> task.attachImage("second.png")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(task::removeImage).isInstanceOf(ConflictException.class);
        assertThat(task.getImageObjectKey()).isEqualTo("first.png");
    }
}
