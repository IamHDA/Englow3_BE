package com.englow3.ai.embedding;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/ai/embeddings")
@PreAuthorize("hasRole('ADMIN')")
class AiEmbeddingAdminController {

    private final AiEmbeddingIndexService service;

    AiEmbeddingAdminController(AiEmbeddingIndexService service) {
        this.service = service;
    }

    @GetMapping
    List<AiEmbeddingIndexDtos.StateResponse> states(@RequestParam(required = false) String status) {
        return service.states(status);
    }

    @PostMapping("/reindex")
    AiEmbeddingIndexDtos.ReindexResponse reindex(@Valid @RequestBody AiEmbeddingIndexDtos.ReindexRequest request) {
        return new AiEmbeddingIndexDtos.ReindexResponse(service.reindex(request.contentType(), request.contentId()));
    }
}
