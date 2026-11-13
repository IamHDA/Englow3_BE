package com.englow3.roadmap.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The level-check weights as the admin screen shows them.
 *
 * @param purposes
 *            DEFAULT first, then every learning purpose, each with the row in force now; a purpose with no row of its
 *            own uses DEFAULT
 * @param history
 *            the latest changes, newest first
 */
public record LevelCheckWeightsResult(List<Purpose> purposes, List<Change> history) {

    /**
     * @param displayName
     *            the purpose's name as learners see it; null for DEFAULT
     * @param inForce
     *            the row in force for this purpose itself, or null when it falls back to DEFAULT
     */
    public record Purpose(String purposeCode, String displayName, Change inForce) {
    }

    public record Change(UUID id, String purposeCode, int vocabulary, int listening, int speaking, int priority,
            Instant effectiveFrom, String changedBy) {
    }
}
