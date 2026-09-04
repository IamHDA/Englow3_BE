package com.englow3.ai.foundation;

public enum AiJobStatus {
    PENDING, PROCESSING, RETRY_SCHEDULED, SUCCEEDED, FAILED, CANCELLED;

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
