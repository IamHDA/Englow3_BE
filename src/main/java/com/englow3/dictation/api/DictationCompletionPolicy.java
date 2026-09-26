package com.englow3.dictation.api;

import java.math.BigDecimal;

public interface DictationCompletionPolicy {

    BigDecimal completionThreshold();

    boolean isCompleted(BigDecimal accuracyPercent);
}
