package com.englow3.speaking.entity;

/**
 * Where one recording stands.
 * <p>
 * There is no RUNNING. Whether a worker currently has the job in hand is the queue's business, and copying it here
 * would be two rows that can disagree about the same fact. QUEUED means "we have it and you have not got a score yet",
 * which is all a learner watching a spinner needs to know.
 */
public enum SpeakingAttemptStatus {

    /** The row exists and the upload URL has been issued; the audio is not there yet. */
    AWAITING_UPLOAD,

    /** The audio arrived and assessment has been asked for. */
    QUEUED,

    ASSESSED,

    /** Assessment will not happen. {@code errorCode} says why. */
    FAILED
}
