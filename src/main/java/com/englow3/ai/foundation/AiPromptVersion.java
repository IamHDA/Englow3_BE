package com.englow3.ai.foundation;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "ai_prompt_versions")
@Getter
public class AiPromptVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private AiPromptTemplate template;

    @Column(nullable = false)
    private int version;

    @Column(name = "system_template", nullable = false)
    private String systemTemplate;

    @Column(name = "user_template", nullable = false)
    private String userTemplate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_schema", columnDefinition = "jsonb")
    private JsonNode responseSchema;

    @Column(nullable = false)
    private boolean active;

    protected AiPromptVersion() {
    }
}
