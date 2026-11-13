package com.englow3.exam.worker;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.exam.repository.FullTestSittingRepository;
import com.englow3.exam.service.FullTestSittingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Settles full test sittings nobody is looking at: ones past their seven days, and ones waiting on a grade whose
 * announcement was lost. Each sitting in its own transaction, so one failure does not hold up the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.exam.full-test-sweep.enabled", havingValue = "true", matchIfMissing = true)
public class FullTestSittingSweeper {

    private final FullTestSittingService sittings;
    private final FullTestSittingRepository sittingRepo;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.exam.full-test-sweep.interval:10m}", initialDelayString = "${app.exam.full-test-sweep.initial-delay:1m}")
    public void sweep() {
        for (var sitting : sittingRepo.findUnsettled(clock.instant(), PageRequest.of(0, 50))) {
            try {
                sittings.refresh(sitting.getId());
            } catch (RuntimeException failure) {
                log.warn("Full test sitting {} not settled yet ({})", sitting.getId(),
                        failure.getClass().getSimpleName());
            }
        }
    }
}
