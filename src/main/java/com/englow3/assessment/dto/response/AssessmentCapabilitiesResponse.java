package com.englow3.assessment.dto.response;

import com.englow3.assessment.dto.result.AssessmentCapabilities;

public record AssessmentCapabilitiesResponse(boolean automaticWriting, boolean automaticSpeaking, boolean humanReview) {
    public static AssessmentCapabilitiesResponse from(AssessmentCapabilities c) {
        return new AssessmentCapabilitiesResponse(c.automaticWriting(), c.automaticSpeaking(), c.humanReview());
    }
}
