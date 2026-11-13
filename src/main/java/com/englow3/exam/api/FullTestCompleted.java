package com.englow3.exam.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A learner's IELTS full test has its overall band. Published inside the transaction that decided it and delivered to
 * listeners after it commits. The exam module neither knows nor depends on who listens.
 */
public record FullTestCompleted(UUID sittingId, UUID userId, UUID fullTestId, BigDecimal overallBand) {
}
