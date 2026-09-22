package com.englow3.ai.client;

import lombok.Getter;

/**
 * A speech assessment request that did not produce a score.
 * <p>
 * Deliberately not a {@code DomainException}: this never reaches a client. It is caught by the job handler and turned
 * into an outcome the queue records, so it carries the one thing that decision needs - whether repeating the request
 * could work - rather than an HTTP status nobody here will map.
 */
@Getter
public class SpeechAssessmentException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public SpeechAssessmentException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }
}
