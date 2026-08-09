package com.englow3.user.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;

/** Both fields are optional - sending them empty is how a learner skips this step. */
public record SetLearningGoalRequest(@Positive BigDecimal targetScore, @Future LocalDate targetDate) {
}
