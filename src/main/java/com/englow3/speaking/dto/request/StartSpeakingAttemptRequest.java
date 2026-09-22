package com.englow3.speaking.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * @param contentType
 *            the format the recording will be in. Asked for up front so an unusable format is refused before the
 *            learner records anything, rather than after.
 */
public record StartSpeakingAttemptRequest(@NotBlank String contentType) {
}
