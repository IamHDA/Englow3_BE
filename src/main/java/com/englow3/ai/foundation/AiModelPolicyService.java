package com.englow3.ai.foundation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelPolicyService {

    private final AiModelPolicyRepository repository;
    private final AiProperties properties;

    AiModelPolicyService(AiModelPolicyRepository repository, AiProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public ResolvedAiPolicy resolve(AiCapability capability) {
        return repository.findById(capability)
                .map(policy -> new ResolvedAiPolicy(policy.getProviderName(), policy.getModelName(),
                        policy.getTemperature().doubleValue(), policy.getMaxOutputTokens(), policy.isEnabled()))
                .orElseGet(() -> new ResolvedAiPolicy(properties.provider(), properties.defaultModel(), 0.2,
                        properties.maxOutputTokens(), properties.enabled()));
    }
}
