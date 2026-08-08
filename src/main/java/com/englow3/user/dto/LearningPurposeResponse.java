package com.englow3.user.dto;

import com.englow3.user.entity.LearningPurpose;

public record LearningPurposeResponse(Integer id, String purposeCode, String displayName) {

    public static LearningPurposeResponse from(LearningPurpose purpose) {
        return new LearningPurposeResponse(purpose.getId(), purpose.getPurposeCode(), purpose.getDisplayName());
    }
}
