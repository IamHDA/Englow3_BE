package com.englow3.ai.entity;

/**
 * Where a queued piece of AI work stands.
 * <p>
 * There is no CANCELLED. Nothing cancels a job today, and a status nobody writes is a branch every reader has to
 * consider for no reason.
 */
public enum AiJobStatus {

    /** Waiting to be picked up, or waiting out a backoff after a retryable failure. */
    PENDING,

    /** Claimed by a worker. A row stuck here past the lock timeout is reclaimed rather than lost. */
    RUNNING,

    SUCCEEDED,

    /** Failed and out of retries. Terminal: the error code and message say why. */
    FAILED
}
