package com.englow3.ai.learningpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BktMasteryCalculatorTest {

    private final BktMasteryCalculator calculator = new BktMasteryCalculator();

    @Test
    void successfulEvidenceIncreasesMastery() {
        BktMasteryCalculator.Update update = calculator.calculate(0.3, 0.1, 0.1, 0.2, true);

        assertThat(update.observed()).isGreaterThan(update.prior());
        assertThat(update.posterior()).isGreaterThan(update.observed()).isLessThanOrEqualTo(0.99);
    }

    @Test
    void unsuccessfulEvidenceReducesObservationBeforeLearningTransition() {
        BktMasteryCalculator.Update update = calculator.calculate(0.7, 0.05, 0.1, 0.2, false);

        assertThat(update.observed()).isLessThan(update.prior());
        assertThat(update.posterior()).isGreaterThan(update.observed());
    }

    @Test
    void posteriorIsBoundedForExtremeValidParameters() {
        assertThat(calculator.calculate(1, 1, 0, 0, true).posterior()).isEqualTo(0.99);
        assertThat(calculator.calculate(0, 0, 0, 0, false).posterior()).isEqualTo(0.01);
    }

    @Test
    void rejectsNonFiniteOrOutOfRangeProbabilities() {
        assertThatThrownBy(() -> calculator.calculate(Double.NaN, 0.1, 0.1, 0.2, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(0.5, 1.1, 0.1, 0.2, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
