package com.englow3.ai.foundation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.englow3.shared.error.ConflictException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class AiGateway {

    private final Map<String, AiTextClient> clients;
    private final AiModelPolicyService policyService;
    private final AiUsageService usageService;
    private final MeterRegistry meterRegistry;

    AiGateway(List<AiTextClient> clients, AiModelPolicyService policyService, AiUsageService usageService,
            MeterRegistry meterRegistry) {
        this.clients = clients.stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(AiTextClient::providerName, Function.identity()));
        this.policyService = policyService;
        this.usageService = usageService;
        this.meterRegistry = meterRegistry;
    }

    public AiTextResult generate(UUID userId, AiCapability capability, String systemPrompt, String userPrompt,
            boolean jsonOutput) {
        ResolvedAiPolicy policy = policyService.resolve(capability);
        if (!policy.enabled()) {
            throw new ConflictException("AI_CAPABILITY_DISABLED", "This AI capability is currently disabled");
        }
        AiTextClient client = clients.get(policy.provider());
        if (client == null) {
            throw new AiProviderException("AI_PROVIDER_NOT_CONFIGURED", "No client is configured for this provider",
                    false);
        }

        usageService.reserve(userId);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            AiTextResult result = client.generate(new AiTextRequest(policy.model(), systemPrompt, userPrompt,
                    policy.temperature(), policy.maxOutputTokens(), jsonOutput));
            BigDecimal estimatedCost = estimateCost(result, policy);
            usageService.recordUsage(userId, result.inputTokens(), result.outputTokens(), estimatedCost);
            Counter.builder("englow3.ai.requests").tag("capability", capability.name())
                    .tag("provider", policy.provider()).tag("outcome", "success").register(meterRegistry).increment();
            return new AiTextResult(result.content(), result.model(), result.inputTokens(), result.outputTokens(),
                    estimatedCost);
        } catch (RuntimeException ex) {
            Counter.builder("englow3.ai.requests").tag("capability", capability.name())
                    .tag("provider", policy.provider()).tag("outcome", "failure").register(meterRegistry).increment();
            throw ex;
        } finally {
            sample.stop(Timer.builder("englow3.ai.latency").tag("capability", capability.name())
                    .tag("provider", policy.provider()).register(meterRegistry));
        }
    }

    private BigDecimal estimateCost(AiTextResult result, ResolvedAiPolicy policy) {
        BigDecimal input = policy.inputCostPerMillion().multiply(BigDecimal.valueOf(result.inputTokens()));
        BigDecimal output = policy.outputCostPerMillion().multiply(BigDecimal.valueOf(result.outputTokens()));
        return input.add(output).divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP);
    }
}
