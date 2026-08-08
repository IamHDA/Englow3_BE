package com.englow3.shared.error;

/**
 * A broken business rule. Carries a code and a message - never an HTTP status:
 * turning this into a response is the web layer's job, in GlobalExceptionHandler.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
