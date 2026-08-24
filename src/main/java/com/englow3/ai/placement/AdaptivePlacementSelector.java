package com.englow3.ai.placement;

import java.util.Comparator;
import java.util.List;

import com.englow3.shared.error.ConflictException;

final class AdaptivePlacementSelector {

    private final IrtCalculator calculator;

    AdaptivePlacementSelector(IrtCalculator calculator) {
        this.calculator = calculator;
    }

    Candidate select(List<Candidate> candidates, double theta) {
        return candidates.stream()
                .max(Comparator
                        .comparingDouble((Candidate candidate) -> calculator.information(theta, candidate.parameters()))
                        .thenComparing(Candidate::itemId, Comparator.reverseOrder()))
                .orElseThrow(() -> new ConflictException("ADAPTIVE_PLACEMENT_POOL_EXHAUSTED",
                        "No eligible calibrated placement item remains"));
    }

    record Candidate(String itemId, IrtCalculator.Parameters parameters) {
    }
}
