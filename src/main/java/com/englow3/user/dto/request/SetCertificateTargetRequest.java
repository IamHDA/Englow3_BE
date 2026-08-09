package com.englow3.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetCertificateTargetRequest(@NotBlank @Size(max = 20) String certificateType) {
}
