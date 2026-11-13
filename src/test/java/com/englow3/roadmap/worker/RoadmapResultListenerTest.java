package com.englow3.roadmap.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.assessment.api.AssessmentAnswerGraded;
import com.englow3.exam.api.ExamSittingScored;
import com.englow3.roadmap.service.RoadmapResultSync;

class RoadmapResultListenerTest {

    private final RoadmapResultSync sync = mock(RoadmapResultSync.class);
    private final RoadmapResultListener listener = new RoadmapResultListener(sync);
    private final UUID learner = UUID.randomUUID();
    private final UUID paper = UUID.randomUUID();

    @Test
    void passesAFullSittingOn() {
        listener.onSittingScored(new ExamSittingScored(UUID.randomUUID(), learner, paper, true));

        verify(sync).examSittingScored(learner, paper);
    }

    @Test
    void ignoresAPracticeOfChosenPartsAndAWrittenAnswer() {
        listener.onSittingScored(new ExamSittingScored(UUID.randomUUID(), learner, paper, false));
        listener.onAnswerGraded(new AssessmentAnswerGraded(UUID.randomUUID(), learner, UUID.randomUUID(), "WRITING"));

        verifyNoInteractions(sync);
    }

    @Test
    void passesASpokenAnswerOn() {
        UUID task = UUID.randomUUID();
        listener.onAnswerGraded(new AssessmentAnswerGraded(UUID.randomUUID(), learner, task, "SPEAKING"));

        verify(sync).speakingAnswerGraded(learner, task);
    }

    /** The grade is already saved; the learner must not see their submission fail because the roadmap lagged. */
    @Test
    void neverFailsTheGradeItWasToldAbout() {
        doThrow(new IllegalStateException("database blip")).when(sync).examSittingScored(any(), any());

        assertThatCode(() -> listener.onSittingScored(new ExamSittingScored(UUID.randomUUID(), learner, paper, true)))
                .doesNotThrowAnyException();
    }
}
