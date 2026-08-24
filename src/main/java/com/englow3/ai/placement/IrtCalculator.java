package com.englow3.ai.placement;

import org.springframework.stereotype.Component;

@Component
class IrtCalculator {

    double probability(double theta, Parameters item) {
        validate(item);
        double logistic = 1.0 / (1.0 + Math.exp(-item.discrimination() * (theta - item.difficulty())));
        return item.guessing() + (1.0 - item.guessing()) * logistic;
    }

    double information(double theta, Parameters item) {
        double probability = probability(theta, item);
        double logistic = (probability - item.guessing()) / (1.0 - item.guessing());
        double derivative = (1.0 - item.guessing()) * item.discrimination() * logistic * (1.0 - logistic);
        return derivative * derivative / Math.max(1.0e-9, probability * (1.0 - probability));
    }

    double update(double theta, boolean correct, Parameters item) {
        double probability = probability(theta, item);
        double logistic = (probability - item.guessing()) / (1.0 - item.guessing());
        double derivative = (1.0 - item.guessing()) * item.discrimination() * logistic * (1.0 - logistic);
        double score = ((correct ? 1.0 : 0.0) - probability) * derivative
                / Math.max(1.0e-9, probability * (1.0 - probability));
        double step = score / Math.max(0.1, information(theta, item));
        return Math.max(-4.0, Math.min(4.0, theta + Math.max(-1.0, Math.min(1.0, step))));
    }

    private void validate(Parameters item) {
        if (!Double.isFinite(item.discrimination()) || !Double.isFinite(item.difficulty())
                || !Double.isFinite(item.guessing()) || item.discrimination() <= 0 || item.discrimination() > 5
                || item.difficulty() < -6 || item.difficulty() > 6 || item.guessing() < 0 || item.guessing() >= 0.5) {
            throw new IllegalArgumentException("Invalid IRT item parameters");
        }
    }

    record Parameters(double discrimination, double difficulty, double guessing) {
    }
}
