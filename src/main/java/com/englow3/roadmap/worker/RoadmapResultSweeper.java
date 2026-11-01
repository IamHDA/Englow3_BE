package com.englow3.roadmap.worker;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.roadmap.service.RoadmapResultSync;

import lombok.RequiredArgsConstructor;

/**
 * The safety net under {@link RoadmapResultListener}: every few minutes, looks again at unfinished level checks and
 * active roadmaps. Picks up a grade whose announcement was lost, and replans a roadmap that fell a week behind even if
 * the learner has stopped visiting. Each item runs on its own, so one bad row does not stop the rest.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.roadmap.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class RoadmapResultSweeper {

    private static final Logger log = LoggerFactory.getLogger(RoadmapResultSweeper.class);

    static final int BATCH = 200;
    /** Bounds one pass; anything past it waits for the next. */
    static final int MAX_PAGES = 50;

    private final RoadmapResultSync sync;

    @Scheduled(fixedDelayString = "${app.roadmap.sweep.interval:10m}", initialDelayString = "${app.roadmap.sweep.initial-delay:1m}")
    public void sweep() {
        sweepAll(sync::unfinishedLevelCheckIds, sync::refreshLevelCheck, "Level check");
        sweepAll(sync::activeRoadmapIds, sync::refreshRoadmap, "Roadmap");
    }

    private void sweepAll(BiFunction<Integer, Integer, List<UUID>> pageOf, Consumer<UUID> refresh, String what) {
        for (int page = 0; page < MAX_PAGES; page++) {
            List<UUID> ids = pageOf.apply(page, BATCH);
            for (UUID id : ids) {
                try {
                    refresh.accept(id);
                } catch (RuntimeException failure) {
                    log.warn("{} {} could not be refreshed: {}", what, id, failure.toString());
                }
            }
            if (ids.size() < BATCH) {
                return;
            }
        }
    }
}
