package com.englow3.ai.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IrtCalculatorTest {
    private final IrtCalculator calculator = new IrtCalculator();

    @Nested
    class Success {

        @Test
        void correctAndIncorrectAnswersMoveAbilityInOppositeDirections() {
            IrtCalculator.Parameters item = new IrtCalculator.Parameters(1.2, 0, 0.2);

            assertThat(calculator.update(0, true, item)).isPositive().isLessThanOrEqualTo(1);
            assertThat(calculator.update(0, false, item)).isNegative().isGreaterThanOrEqualTo(-1);
        }

        @Test
        void updatesRemainInsideSupportedAbilityRange() {
            IrtCalculator.Parameters item = new IrtCalculator.Parameters(5, 0, 0);

            assertThat(calculator.update(4, true, item)).isEqualTo(4);
            assertThat(calculator.update(-4, false, item)).isEqualTo(-4);
        }

        @Test
        void informationIsHigherNearItemDifficulty() {
            IrtCalculator.Parameters item = new IrtCalculator.Parameters(1.5, 0.5, 0.1);

            assertThat(calculator.information(0.5, item)).isGreaterThan(calculator.information(-3, item));
            assertThat(calculator.information(0.5, item)).isGreaterThan(calculator.information(4, item));
        }

        @Test
        void rejectsInvalidParameters() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.probability(0, new IrtCalculator.Parameters(0, 0, 0)));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> calculator.probability(0, new IrtCalculator.Parameters(1, 0, 0.5)));
        }

    }

}
