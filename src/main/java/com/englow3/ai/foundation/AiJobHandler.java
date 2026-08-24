package com.englow3.ai.foundation;

public interface AiJobHandler {

    String jobType();

    AiJobExecutionResult execute(AiJob job);
}
