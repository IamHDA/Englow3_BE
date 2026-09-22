package com.englow3.ai.client;

import lombok.Getter;

/**
 * A generation request that did not produce text.
 * <p>
 * Like {@link SpeechAssessmentException}, deliberately not a {@code DomainException}: it never reaches a client. The
 * job handler catches it and turns it into an outcome the queue records, so it carries the one thing that decision
 * needs - whether repeating the request could work.
 */
@Getter
public class LlmException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public LlmException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }
}
