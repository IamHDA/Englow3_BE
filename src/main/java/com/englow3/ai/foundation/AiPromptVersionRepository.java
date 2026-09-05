package com.englow3.ai.foundation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AiPromptVersionRepository extends JpaRepository<AiPromptVersion, UUID> {

    Optional<AiPromptVersion> findFirstByTemplateTemplateKeyAndActiveTrueOrderByVersionDesc(String templateKey);
}
