package com.englow3.speaking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * @param contentType
 *            the format the recording will be in. Asked for up front so an unusable format is refused before the
 *            learner records anything, rather than after.
 * @param contentLength
 *            exactly how many bytes will be uploaded. The client knows this already - the recording exists before the
 *            URL is asked for - and stating it is what lets the size be bound into the signature. A presigned PUT never
 *            passes through this application, so there is nowhere else a limit could be enforced.
 */
public record StartSpeakingAttemptRequest(@NotBlank String contentType, @Positive long contentLength) {
}
