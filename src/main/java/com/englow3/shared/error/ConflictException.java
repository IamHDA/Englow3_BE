package com.englow3.shared.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
