package com.englow3.ai.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.ConflictException;

class AdaptivePlacementSelectorTest {
    private final AdaptivePlacementSelector selector = new AdaptivePlacementSelector(new IrtCalculator());

    private AdaptivePlacementSelector.Candidate candidate(String id, double difficulty) {
        return new AdaptivePlacementSelector.Candidate(id, new IrtCalculator.Parameters(1.2, difficulty, 0.2));
    }

    @Nested
    class Success {

        @Test
        void selectsMostInformativeItemForCurrentAbility() {
            AdaptivePlacementSelector.Candidate far = candidate("far", 3);
            AdaptivePlacementSelector.Candidate near = candidate("near", 0);

            assertThat(selector.select(List.of(far, near), 0).itemId()).isEqualTo("near");
        }

        @Test
        void selectionIsDeterministicWhenInformationTies() {
            assertThat(selector.select(List.of(candidate("B", 0), candidate("A", 0)), 0).itemId()).isEqualTo("A");
        }

    }

    @Nested
    class Failure {

        @Test
        void emptyPoolHasExplicitFailure() {
            assertThatThrownBy(() -> selector.select(List.of(), 0)).isInstanceOf(ConflictException.class)
                    .hasMessageContaining("No eligible calibrated");
        }

    }

}
