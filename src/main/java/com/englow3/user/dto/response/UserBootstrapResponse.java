package com.englow3.user.dto.response;

import com.englow3.user.dto.result.UserBootstrapResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserBootstrapResponse(UserInformationResponse user,
        @Schema(nullable = true) OnboardingStateResponse onboardingState) {
    public static UserBootstrapResponse from(UserBootstrapResult result, String publicBaseUrl) {
        return new UserBootstrapResponse(UserInformationResponse.from(result.user(), publicBaseUrl),
                result.onboardingState() == null ? null : OnboardingStateResponse.from(result.onboardingState()));
    }
}
