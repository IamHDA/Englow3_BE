package com.englow3.ai.embedding;

import java.lang.management.ManagementFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.englow3.ai.foundation.AiEmbeddingClient;
import com.englow3.ai.foundation.AiProperties;
import com.englow3.ai.foundation.AiProviderException;

@Component
class AiEmbeddingIndexWorker {

    private static final Logger log = LoggerFactory.getLogger(AiEmbeddingIndexWorker.class);

    private final AiEmbeddingIndexService indexService;
    private final AiEmbeddingClient embeddingClient;
    private final AiProperties properties;
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + ":embedding";

    AiEmbeddingIndexWorker(AiEmbeddingIndexService indexService, AiEmbeddingClient embeddingClient,
            AiProperties properties) {
        this.indexService = indexService;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.ai.embedding.worker-delay:3s}")
    void poll() {
        if (!properties.enabled()) {
            return;
        }
        indexService.recoverStale(properties.worker().lockTimeout());
        for (int i = 0; i < properties.worker().batchSize(); i++) {
            AiEmbeddingIndexService.ClaimedEmbedding claim = indexService.claimNext(workerId);
            if (claim == null) {
                return;
            }
            execute(claim);
        }
    }

    private void execute(AiEmbeddingIndexService.ClaimedEmbedding claim) {
        AiEmbeddingIndexService.Content content = indexService.currentContent(claim.contentType(), claim.contentId());
        if (content == null || !content.approved()
                || !AiEmbeddingIndexService.sha256(content.text()).equals(claim.contentHash())) {
            indexService.complete(claim, new AiEmbeddingClient.Result(
                    java.util.Collections.nCopies(AiEmbeddingClient.DIMENSIONS, 0.0), "stale", 0));
            return;
        }
        try {
            indexService.complete(claim, embeddingClient.embed(content.text()));
        } catch (AiProviderException ex) {
            log.warn("Embedding failed for {}:{} with code {}", claim.contentType(), claim.contentId(), ex.code());
            indexService.fail(claim, ex.code(), "The AI service could not index this content", ex.retryable());
        } catch (RuntimeException ex) {
            log.error("Embedding failed unexpectedly for {}:{}", claim.contentType(), claim.contentId(), ex);
            indexService.fail(claim, "AI_EMBEDDING_INDEX_FAILED", "Embedding indexing failed unexpectedly", true);
        }
    }
}
