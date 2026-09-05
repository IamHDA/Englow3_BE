package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AiGatewayTest {
    @Nested
    class Success {

        @Test
        void calculatesAndRecordsConfiguredTokenCost() {
            AiModelPolicyService policies = mock(AiModelPolicyService.class);
            AiUsageService usage = mock(AiUsageService.class);
            AiTextClient client = new AiTextClient() {
                @Override
                public String providerName() {
                    return "provider";
                }

                @Override
                public AiTextResult generate(AiTextRequest request) {
                    return new AiTextResult("answer", request.model(), 1000, 500);
                }
            };
            when(policies.resolve(AiCapability.TUTOR)).thenReturn(new ResolvedAiPolicy("provider", "model", 0.2, 1000,
                    true, new BigDecimal("0.50"), new BigDecimal("1.50")));
            AiGateway gateway = new AiGateway(List.of(client), policies, usage, new SimpleMeterRegistry());
            UUID userId = UUID.randomUUID();

            AiTextResult result = gateway.generate(userId, AiCapability.TUTOR, "system", "user", false);

            assertThat(result.estimatedCost()).isEqualByComparingTo("0.001250");
            verify(usage).recordUsage(userId, 1000, 500, new BigDecimal("0.001250"));
        }

    }

}
