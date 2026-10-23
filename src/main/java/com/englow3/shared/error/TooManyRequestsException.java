package com.englow3.shared.error;

/** The caller is asking more often than this action allows. Answered with 429 so a client knows to wait, not to fix. */
public final class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String code, String message) {
        super(code, message);
    }
}
