package com.englow3.ai.foundation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class AiEvaluationGateway {

    private final Map<String, AiTextClient> clients;

    AiEvaluationGateway(List<AiTextClient> clients) {
        this.clients = clients.stream()
                .collect(Collectors.toUnmodifiableMap(AiTextClient::providerName, Function.identity()));
    }

    public EvaluationResult evaluate(EvaluationRequest request) {
        AiTextClient client = clients.get(request.provider());
        if (client == null) {
            throw new AiProviderException("AI_EVALUATION_PROVIDER_NOT_CONFIGURED",
                    "The evaluation provider is not configured", false);
        }
        long started = System.nanoTime();
        AiTextResult result = client.generate(new AiTextRequest(request.model(), request.systemPrompt(),
                request.userPrompt(), request.temperature(), request.maxOutputTokens(), true));
        long latencyMs = java.time.Duration.ofNanos(System.nanoTime() - started).toMillis();
        BigDecimal cost = request.inputCostPerMillion().multiply(BigDecimal.valueOf(result.inputTokens()))
                .add(request.outputCostPerMillion().multiply(BigDecimal.valueOf(result.outputTokens())))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        return new EvaluationResult(result.content(), result.model(), result.inputTokens(), result.outputTokens(), cost,
                Math.toIntExact(Math.min(Integer.MAX_VALUE, latencyMs)));
    }

    public record EvaluationRequest(String provider, String model, String systemPrompt, String userPrompt,
            double temperature, int maxOutputTokens, BigDecimal inputCostPerMillion, BigDecimal outputCostPerMillion) {
    }

    public record EvaluationResult(String content, String model, int inputTokens, int outputTokens,
            BigDecimal estimatedCost, int latencyMs) {
    }
}
