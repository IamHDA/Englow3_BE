package com.englow3.roadmap.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.englow3.assessment.api.AssessmentAnswerGraded;
import com.englow3.exam.api.ExamSittingScored;
import com.englow3.roadmap.service.RoadmapResultSync;

import lombok.RequiredArgsConstructor;

/**
 * Hears that a sitting was scored or a spoken answer graded, once the grade has committed, and moves level checks and
 * roadmaps on straight away - whether or not the learner ever opens the roadmap page.
 * <p>
 * A failure here is logged and left to the periodic sweep: the grade itself is already saved and must not be reported
 * to the learner as failed because the roadmap could not keep up.
 */
@Component
@RequiredArgsConstructor
public class RoadmapResultListener {

    private static final Logger log = LoggerFactory.getLogger(RoadmapResultListener.class);

    private final RoadmapResultSync sync;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSittingScored(ExamSittingScored event) {
        if (!event.full()) {
            // A practice of chosen parts is neither a level check nor a milestone.
            return;
        }
        try {
            sync.examSittingScored(event.userId(), event.examId());
        } catch (RuntimeException failure) {
            log.warn("Roadmap did not take sitting {} yet; the sweep will retry: {}", event.attemptId(),
                    failure.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAnswerGraded(AssessmentAnswerGraded event) {
        if (!"SPEAKING".equals(event.skill())) {
            return;
        }
        try {
            sync.speakingAnswerGraded(event.userId(), event.taskId());
        } catch (RuntimeException failure) {
            log.warn("Roadmap did not take answer {} yet; the sweep will retry: {}", event.attemptId(),
                    failure.toString());
        }
    }
}
