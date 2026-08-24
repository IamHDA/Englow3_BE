package com.englow3.ai.learningpath;

import org.springframework.stereotype.Component;

@Component
class BktMasteryCalculator {

    static final int ALGORITHM_VERSION = 1;

    Update calculate(double prior, double learn, double slip, double guess, boolean successful) {
        requireProbability(prior, "prior");
        requireProbability(learn, "learn");
        requireProbability(slip, "slip");
        requireProbability(guess, "guess");
        double numerator = successful ? prior * (1 - slip) : prior * slip;
        double denominator = successful ? numerator + (1 - prior) * guess : numerator + (1 - prior) * (1 - guess);
        double observed = denominator == 0 ? prior : numerator / denominator;
        double posterior = Math.min(0.99, Math.max(0.01, observed + (1 - observed) * learn));
        return new Update(prior, observed, posterior);
    }

    private void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be a finite probability");
        }
    }

    record Update(double prior, double observed, double posterior) {
    }
}
