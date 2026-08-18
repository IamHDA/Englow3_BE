package com.englow3.user.dto.request;

import com.englow3.user.entity.CertificateType;
import jakarta.validation.constraints.NotNull;

public record SetCertificateTargetRequest(@NotNull CertificateType certificateType) {
}
