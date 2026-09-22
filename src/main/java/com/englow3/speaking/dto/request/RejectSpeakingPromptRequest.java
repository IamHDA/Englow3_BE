package com.englow3.speaking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param note
 *            what the author has to change. Blank is refused here and again at the entity.
 */
public record RejectSpeakingPromptRequest(@NotBlank @Size(max = 2000) String note) {
}
