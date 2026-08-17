package com.englow3.user.dto.command;

import com.englow3.user.entity.CertificateType;

public record SetCertificateTargetCommand(CertificateType certificateType) {
}
