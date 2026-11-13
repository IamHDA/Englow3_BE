package com.englow3.user.api;

import java.math.BigDecimal;
import java.util.Optional;

/** Minimal learning context for the authenticated learner, with no identity or contact data. */
public interface LearnerContextReader {
    Optional<LearningContext> current();

    record LearningContext(String currentLevel, boolean measuredByPlacement, String targetCertificate,
            BigDecimal targetScore) {
    }
}
