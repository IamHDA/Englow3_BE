package com.englow3.user.dto.result;

import com.englow3.user.entity.LearningPurpose;

public record LearningPurposeResult(Integer id, String purposeCode, String displayName) {

    public static LearningPurposeResult from(LearningPurpose purpose) {
        return new LearningPurposeResult(purpose.getId(), purpose.getPurposeCode(), purpose.getDisplayName());
    }
}
