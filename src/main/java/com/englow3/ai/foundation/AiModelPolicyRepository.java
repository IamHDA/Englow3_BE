package com.englow3.ai.foundation;

import org.springframework.data.jpa.repository.JpaRepository;

interface AiModelPolicyRepository extends JpaRepository<AiModelPolicy, AiCapability> {
}
