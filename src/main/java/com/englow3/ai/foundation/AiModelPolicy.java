package com.englow3.ai.foundation;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "ai_model_policies")
@Getter
public class AiModelPolicy {

    @Id
    @Enumerated(EnumType.STRING)
    private AiCapability capability;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(nullable = false)
    private BigDecimal temperature;

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens;

    @Column(nullable = false)
    private boolean enabled;

    protected AiModelPolicy() {
    }
}
