package com.englow3.exam.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param note
 *            what the author has to change. Blank is refused: a rejection with no reason cannot be acted on.
 */
public record RejectExamRequest(@NotBlank @Size(max = 2000) String note) {
}
