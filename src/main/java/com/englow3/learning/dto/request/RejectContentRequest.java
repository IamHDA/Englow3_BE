package com.englow3.learning.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * One shape for all three content types: the field is the same sentence in each, and three identical records would be
 * three things to keep in step.
 *
 * @param note
 *            what the author has to change. Blank is refused, here and again at the entity, because a rejection with no
 *            reason cannot be acted on.
 */
public record RejectContentRequest(@NotBlank @Size(max = 2000) String note) {
}
