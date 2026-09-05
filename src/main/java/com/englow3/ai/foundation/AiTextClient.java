package com.englow3.ai.foundation;

public interface AiTextClient {

    String providerName();

    AiTextResult generate(AiTextRequest request);
}
