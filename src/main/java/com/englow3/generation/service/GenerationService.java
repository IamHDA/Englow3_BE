package com.englow3.generation.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.generation.dto.result.GenerationAllowanceResult;
import com.englow3.generation.dto.result.GenerationRequestResult;
import com.englow3.generation.entity.GenerationKind;

public interface GenerationService {

    /**
     * Starts a request for the signed-in user. Refused with GENERATION_DAILY_LIMIT past three counted requests today,
     * and GENERATION_IN_PROGRESS while one of theirs is still running.
     */
    GenerationRequestResult request(GenerationKind kind, String brief);

    Page<GenerationRequestResult> list(Pageable page);

    GenerationRequestResult detail(UUID id);

    GenerationAllowanceResult allowance();
}
