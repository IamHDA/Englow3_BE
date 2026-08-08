package com.englow3.user.dto;

import com.englow3.user.entity.CertificateLevel;

/** A null level means "I don't know" - the learner is then sent to a placement test or a quiz. */
public record SetCurrentLevelRequest(CertificateLevel level) {
}
