package com.englow3.generation.service;

import java.util.UUID;

/** Commits generated drafts and their checkpoint together, so a retried job cannot duplicate them. */
public interface GenerationArtifacts {
    void assemble(UUID requestId, String builtJson);

    void finish(UUID requestId);
}
