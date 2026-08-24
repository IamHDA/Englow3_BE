package com.englow3.user.dto.command;

import com.englow3.user.entity.CertificateLevel;

public record SetCurrentLevelCommand(CertificateLevel level) {
}
