package com.englow3.generation.entity;

/** Only ever moves forward: QUEUED -> RUNNING -> COMPLETED or FAILED. */
public enum GenerationStatus {
    QUEUED, RUNNING, COMPLETED, FAILED;

    public boolean finished() {
        return this == COMPLETED || this == FAILED;
    }
}
