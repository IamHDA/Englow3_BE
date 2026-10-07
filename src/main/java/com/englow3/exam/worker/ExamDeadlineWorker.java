package com.englow3.exam.worker;

import java.time.Clock;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.service.ExamAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamDeadlineWorker {
    private final ExamAttemptRepository attemptRepo;
    private final ExamAttemptService attempts;
    private final Clock clock;

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
}
