package com.englow3.roadmap.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.roadmap.dto.result.LevelCheckWeightsResult;

public record LevelCheckWeightsResponse(List<PurposeResponse> purposes, List<ChangeResponse> history) {

    public static LevelCheckWeightsResponse from(LevelCheckWeightsResult r) {
        return new LevelCheckWeightsResponse(
                r.purposes().stream()
                        .map(p -> new PurposeResponse(p.purposeCode(), p.displayName(),
                                p.inForce() == null ? null : ChangeResponse.from(p.inForce())))
                        .toList(),
                r.history().stream().map(ChangeResponse::from).toList());
    }

    /**
     * @param inForce
     *            the purpose's own row in force now, or null when it falls back to DEFAULT
     */
    public record PurposeResponse(String purposeCode, String displayName, ChangeResponse inForce) {
    }

    public record ChangeResponse(UUID id, String purposeCode, int vocabulary, int listening, int speaking, int priority,
            Instant effectiveFrom, String changedBy) {

        static ChangeResponse from(LevelCheckWeightsResult.Change c) {
            return new ChangeResponse(c.id(), c.purposeCode(), c.vocabulary(), c.listening(), c.speaking(),
                    c.priority(), c.effectiveFrom(), c.changedBy());
        }
    }
}
