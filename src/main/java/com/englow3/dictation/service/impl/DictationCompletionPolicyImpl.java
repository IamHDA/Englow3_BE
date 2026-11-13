package com.englow3.dictation.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.englow3.dictation.api.DictationCompletionPolicy;
import com.englow3.dictation.helper.DictationScorer;

@Component
public class DictationCompletionPolicyImpl implements DictationCompletionPolicy {

    @Override
    public BigDecimal completionThreshold() {
        return DictationScorer.COMPLETION_THRESHOLD;
    }

    @Override
    public boolean isCompleted(BigDecimal accuracyPercent) {
        return DictationScorer.cleared(accuracyPercent);
    }
}
