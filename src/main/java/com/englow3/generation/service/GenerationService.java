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

    /**
     * Picks a failed request up again at the step it stopped at, keeping what the model already wrote. It runs as the
     * person who asked for it, so their limits apply: refused with GENERATION_IN_PROGRESS while another of theirs is
     * running, GENERATION_DAILY_LIMIT when a request of today would take them past the daily limit, and
     * GENERATION_NOT_RESUMABLE or GENERATION_RESUME_LIMIT from the request itself.
     */
    GenerationRequestResult resume(UUID id);

    Page<GenerationRequestResult> list(Pageable page);

    GenerationRequestResult detail(UUID id);

    GenerationAllowanceResult allowance();
}
