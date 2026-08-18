package com.englow3.shared.error;

/** The input itself does not make sense for this caller. */
public final class BadRequestException extends DomainException {

    public BadRequestException(String code, String message) {
        super(code, message);
    }
}
