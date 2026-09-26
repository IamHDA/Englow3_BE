package com.englow3.user.entity;

import java.math.BigDecimal;

public enum CertificateType {
    /** Bands 0 to 9 in half steps. */
    IELTS(new BigDecimal("0"), new BigDecimal("9"), new BigDecimal("0.5")),
    /** 10 to 990 in steps of 5. */
    TOEIC(new BigDecimal("10"), new BigDecimal("990"), new BigDecimal("5"));

    private final BigDecimal minScore;
    private final BigDecimal maxScore;
    private final BigDecimal step;

    CertificateType(BigDecimal minScore, BigDecimal maxScore, BigDecimal step) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.step = step;
    }

    /** Whether this certificate can award the score at all - an IELTS 45 or a TOEIC 7 is not a goal, it is a typo. */
    public boolean isValidScore(BigDecimal score) {
        return score.compareTo(minScore) >= 0 && score.compareTo(maxScore) <= 0
                && score.subtract(minScore).remainder(step).signum() == 0;
    }

    public String scoreRange() {
        return "%s-%s in steps of %s".formatted(minScore.toPlainString(), maxScore.toPlainString(),
                step.toPlainString());
    }
}
