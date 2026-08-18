package com.englow3.user.dto.response;

import com.englow3.user.dto.result.LearningPurposeResult;

public record LearningPurposeResponse(Integer id, String purposeCode, String displayName) {

    public static LearningPurposeResponse from(LearningPurposeResult result) {
        return new LearningPurposeResponse(result.id(), result.purposeCode(), result.displayName());
    }
}
