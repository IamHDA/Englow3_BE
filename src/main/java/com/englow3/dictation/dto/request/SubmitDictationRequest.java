package com.englow3.dictation.dto.request;

import jakarta.validation.constraints.Size;

/** An empty answer is a real answer - it scores zero rather than being rejected. */
public record SubmitDictationRequest(@Size(max = 2000) String response) {
}
