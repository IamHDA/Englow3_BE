package com.englow3.ai.foundation;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "ai_prompt_templates")
@Getter
public class AiPromptTemplate {

    @Id
    private UUID id;

    @Column(name = "template_key", nullable = false, unique = true)
    private String templateKey;

    @Column(nullable = false)
    private String description;

    protected AiPromptTemplate() {
    }
}
