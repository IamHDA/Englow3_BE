package com.englow3.shared.error;

/** The caller is known but is not allowed to do this. */
public final class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }
}
