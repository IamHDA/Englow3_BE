package com.englow3.exam.worker;

import java.time.Clock;
import java.time.Duration;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.service.ExamAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamDeadlineWorker {
    private final ExamAttemptRepository attemptRepo;
    private final ExamAttemptService attempts;
    private final Clock clock;

    @Value("${app.exam.full-attempt-grace:PT2M}")
    private Duration fullAttemptGrace;

    @Scheduled(fixedDelayString = "${app.exam.deadline-check-ms:10000}")
    public void finalizeDeadlines() {
        for (var attempt : attemptRepo.findTop50ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                ExamAttemptStatus.IN_PROGRESS, clock.instant())) {
            try {
                attempts.finalizeExpired(attempt.getId());
            } catch (RuntimeException failure) {
                log.warn("Exam deadline finalization deferred for {} ({})", attempt.getId(),
                        failure.getClass().getSimpleName());
            }
        }
    }

    /** A full sitting is left means it is over: finalize the ones the browser has stopped reporting on. */
    @Scheduled(fixedDelayString = "${app.exam.deadline-check-ms:10000}")
    public void finalizeAbandoned() {
        for (var attempt : attemptRepo.findAbandoned(clock.instant().minus(fullAttemptGrace), PageRequest.of(0, 50))) {
            try {
                attempts.finalizeAbandoned(attempt.getId());
            } catch (RuntimeException failure) {
                log.warn("Abandoned exam finalization deferred for {} ({})", attempt.getId(),
                        failure.getClass().getSimpleName());
            }
        }
    }
}
