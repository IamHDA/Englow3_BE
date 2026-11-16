package com.englow3.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** The highest seq the caller has seen. Boxed so that a missing field is refused rather than read as 0. */
public record MarkReadRequest(@NotNull @PositiveOrZero Long upToSeq) {
}
