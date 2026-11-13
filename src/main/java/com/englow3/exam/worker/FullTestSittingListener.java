package com.englow3.exam.worker;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.englow3.assessment.api.AssessmentAnswerGraded;
import com.englow3.exam.api.ExamSittingScored;
import com.englow3.exam.service.FullTestSittingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps full test sittings up to date without anyone opening them: a scored Listening + Reading sitting or a graded
 * Writing or Speaking answer settles the learner's open sittings once it has committed, and the sweep catches what an
 * announcement missed - events here are not durable - and closes sittings whose seven days ran out
 * ({@link FullTestSittingSweeper}).
 * <p>
 * A failure is logged and left to the next sweep: the grade itself is saved and must not be reported as failed because
 * the full test could not keep up.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FullTestSittingListener {

    private final FullTestSittingService sittings;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSittingScored(ExamSittingScored event) {
        if (!event.full()) {
            return;
        }
        refreshFor(event.userId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAnswerGraded(AssessmentAnswerGraded event) {
        refreshFor(event.userId());
    }

    private void refreshFor(java.util.UUID userId) {
        try {
            sittings.refreshFor(userId);
        } catch (RuntimeException failure) {
            log.warn("Full test sittings of {} not settled yet; the sweep will retry: {}", userId, failure.toString());
        }
    }
}
