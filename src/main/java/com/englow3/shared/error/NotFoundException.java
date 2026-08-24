package com.englow3.shared.error;

/** Something the caller referred to does not exist. */
public final class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }
}
