package com.englow3.shared.error;

/** The request is well formed but the current state forbids it. */
public final class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
