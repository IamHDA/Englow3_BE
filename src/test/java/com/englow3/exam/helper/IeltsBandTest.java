package com.englow3.exam.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class IeltsBandTest {

    private static BigDecimal b(String value) {
        return new BigDecimal(value);
    }

    @Test
    void aQuarterRoundsUpToTheHalf() {
        assertThat(IeltsBand.round(b("6.25"))).isEqualByComparingTo("6.5");
    }

    @Test
    void threeQuartersRoundUpToTheNextWholeBand() {
        assertThat(IeltsBand.round(b("6.75"))).isEqualByComparingTo("7.0");
    }

    @Test
    void anythingElseGoesToTheNearestHalf() {
        assertThat(IeltsBand.round(b("6.375"))).isEqualByComparingTo("6.5");
        assertThat(IeltsBand.round(b("6.125"))).isEqualByComparingTo("6.0");
    }

    @Test
    void theOverallBandIsTheRoundedMeanOfTheFourSkills() {
        assertThat(IeltsBand.mean(List.of(b("7.0"), b("6.5"), b("6.0"), b("6.0")))).isEqualByComparingTo("6.5");
    }

    @Test
    void writingCountsTaskTwoTwice() {
        assertThat(IeltsBand.writing(b("5.0"), b("7.0"))).isEqualByComparingTo("6.5");
    }

    @Test
    void bandsAlignWithCefrLevels() {
        assertThat(IeltsBand.cefr(b("3.5"))).isEqualTo("A2");
        assertThat(IeltsBand.cefr(b("5.0"))).isEqualTo("B1");
        assertThat(IeltsBand.cefr(b("5.5"))).isEqualTo("B2");
        assertThat(IeltsBand.cefr(b("7.0"))).isEqualTo("C1");
        assertThat(IeltsBand.cefr(b("8.5"))).isEqualTo("C2");
    }
}
