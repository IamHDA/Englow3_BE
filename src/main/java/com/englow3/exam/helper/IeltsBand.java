package com.englow3.exam.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** IELTS band arithmetic (docs/decisions.md, D-IELTS-6). Pure. */
public final class IeltsBand {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);

    private IeltsBand() {
    }

    /** To the nearest half band, a .25 or .75 remainder rounding up - the rule IELTS uses for the overall band. */
    public static BigDecimal round(BigDecimal value) {
        return value.multiply(TWO).setScale(0, RoundingMode.HALF_UP).divide(TWO).setScale(1, RoundingMode.UNNECESSARY);
    }

    /** The mean of the bands, rounded. */
    public static BigDecimal mean(List<BigDecimal> bands) {
        BigDecimal sum = bands.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return round(sum.divide(BigDecimal.valueOf(bands.size()), 4, RoundingMode.HALF_UP));
    }

    /** Writing: Task 2 counts twice as much as Task 1. */
    public static BigDecimal writing(BigDecimal task1, BigDecimal task2) {
        return round(task1.add(task2.multiply(TWO)).divide(THREE, 4, RoundingMode.HALF_UP));
    }

    /** The CEFR level Cambridge aligns a band with: 4.0-5.0 B1, 5.5-6.5 B2, 7.0-8.0 C1, 8.5-9.0 C2. */
    public static String cefr(BigDecimal band) {
        if (band.compareTo(new BigDecimal("8.5")) >= 0) {
            return "C2";
        }
        if (band.compareTo(new BigDecimal("7.0")) >= 0) {
            return "C1";
        }
        if (band.compareTo(new BigDecimal("5.5")) >= 0) {
            return "B2";
        }
        if (band.compareTo(new BigDecimal("4.0")) >= 0) {
            return "B1";
        }
        return band.compareTo(new BigDecimal("3.0")) >= 0 ? "A2" : "A1";
    }
}
