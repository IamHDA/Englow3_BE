package com.englow3.ai.foundation;

public interface AiJobHandler {

    String jobType();

    AiJobExecutionResult execute(AiJob job);

    default void onFailure(AiJob job, boolean willRetry) {
    }
}
