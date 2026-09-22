package com.englow3.ai.service;

import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobType;

/**
 * What a module plugs in to have its own kind of job run.
 * <p>
 * The interface lives here and the implementations live in the modules that own the work, so the queue never imports a
 * business module. {@code speaking} depends on {@code ai} to enqueue; {@code ai} depends only on this interface, and
 * Spring supplies the implementation. No cycle, and adding a second job type touches nothing in here.
 */
public interface AiJobHandler {

    AiJobType handles();

    /**
     * Runs one attempt. Called outside any transaction, deliberately: this is where the provider is called, and holding
     * a database connection open for the length of a network round trip is how a connection pool runs dry.
     * <p>
     * It must not throw for an ordinary failure - a returned {@link Outcome} carries whether the failure is worth
     * retrying, which an exception cannot say. The worker treats a thrown exception as a retryable failure, because
     * that is the safer reading of a bug.
     */
    Outcome run(AiJob job);

    /**
     * @param outputPayload
     *            JSON to store on success, ignored otherwise
     * @param retryable
     *            whether the same request could work later. False for anything the input itself makes impossible - a
     *            recording in a format the provider does not accept will not become acceptable in ten minutes.
     */
    record Outcome(boolean success, String outputPayload, String errorCode, String errorMessage, boolean retryable) {

        public static Outcome succeeded(String outputPayload) {
            return new Outcome(true, outputPayload, null, null, false);
        }

        /** A failure worth another go: an outage, a timeout, a rate limit. */
        public static Outcome transientFailure(String errorCode, String errorMessage) {
            return new Outcome(false, null, errorCode, errorMessage, true);
        }

        /** A failure no retry can fix. Fails the job on the spot rather than spending the budget. */
        public static Outcome permanentFailure(String errorCode, String errorMessage) {
            return new Outcome(false, null, errorCode, errorMessage, false);
        }
    }
}
