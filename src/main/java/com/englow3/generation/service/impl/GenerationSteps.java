package com.englow3.generation.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.generation.entity.GenerationRequest;
import com.englow3.generation.repository.GenerationRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Moves a request through its steps, one AI job each. {@link #work} reads what the next attempt needs; {@link #apply}
 * takes its result inside the queue's completion transaction and enqueues the step after it, so a step is only ever
 * recorded once and the next one only ever asked for after it.
 */
@Component
@RequiredArgsConstructor
public class GenerationSteps {

    /** The steps the model writes; everything after them builds, records and submits. */
    static final int WRITING_STEPS = 11;

    private final GenerationRequestRepository requestRepo;
    private final AiJobQueue queue;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** Everything one attempt at a step needs, read before any network call. */
    public record Work(UUID requestId, String step, int attempt, UUID requestedByUserId, String brief,
            JsonNode components, JsonNode retryContext, JsonNode assembly, UUID examId, List<UUID> taskIds,
            List<String> usedTopics) {

        public boolean writes() {
            return GenerationRequest.STEPS.indexOf(step) < WRITING_STEPS;
        }
    }

    /** Asks for the step the request is at. Called in the transaction that recorded the step before it. */
    void enqueue(GenerationRequest request) {
        ObjectNode payload = mapper.createObjectNode().put("step", request.getStep()).put("attempt",
                request.getAttempt());
        queue.enqueueContentGeneration(request.getId(), payload.toString(),
                "generation:%s:%s:%d".formatted(request.getId(), request.getStep(), request.getAttempt()),
                request.getRequestedByUserId());
    }

    /** Null when the request has moved on or finished: a late or repeated job then does nothing. */
    @Transactional(readOnly = true)
    public Work work(UUID requestId, String step) {
        GenerationRequest r = requestRepo.findById(requestId).orElse(null);
        if (r == null || !r.isAt(step)) {
            return null;
        }
        List<UUID> taskIds = new ArrayList<>();
        read(r.getTaskIds()).forEach(id -> taskIds.add(UUID.fromString(id.asText())));
        return new Work(r.getId(), step, r.getAttempt(), r.getRequestedByUserId(), r.getBrief(),
                read(r.getComponents()), r.getRetryContext() == null ? null : read(r.getRetryContext()),
                r.getAssembly() == null ? null : read(r.getAssembly()), r.getExamId(), taskIds,
                "plan".equals(step) ? usedTopics() : List.of());
    }

    /**
     * Records what a step produced and asks for the next one, or for the same step again when the model has to fix its
     * reply. Runs in the queue's completion transaction: no network calls.
     */
    public void apply(UUID requestId, String step, String outputJson) {
        GenerationRequest request = requestRepo.findById(requestId).orElse(null);
        if (request == null || !request.isAt(step)) {
            return;
        }
        JsonNode output = read(outputJson);
        var now = clock.instant();
        switch (step) {
            case "assemble" -> {
                ObjectNode assembly = mapper.createObjectNode();
                assembly.set("chart_png_base64", output.path("chart_png_base64"));
                assembly.set("listening", output.path("listening"));
                request.recordAssembled(UUID.fromString(output.path("examId").asText()),
                        output.path("taskIds").toString(), assembly.toString(), now);
            }
            case "finish" -> request.complete(UUID.fromString(output.path("fullTestId").asText()), now);
            default -> {
                if (GenerationRequest.STEPS.indexOf(step) >= WRITING_STEPS) {
                    request.recordDone(step, now);
                } else if (output.path("problems").isEmpty()) {
                    ObjectNode components = (ObjectNode) read(request.getComponents());
                    components.set(step, output.path("reply"));
                    request.recordWritten(step, components.toString(), now);
                } else {
                    ObjectNode retry = mapper.createObjectNode();
                    retry.set("reply", output.path("reply"));
                    retry.set("raw", output.path("raw"));
                    retry.set("problems", output.path("problems"));
                    boolean again = request.recordProblems(step, output.path("problems").toString(), retry.toString(),
                            now);
                    requestRepo.saveAndFlush(request);
                    if (again) {
                        enqueue(request);
                    }
                    return;
                }
            }
        }
        requestRepo.saveAndFlush(request);
        if (!request.getStatus().finished()) {
            enqueue(request);
        }
    }

    /** The queue gave up on a step: an outage that outlasted its retries, or a failure no retry can fix. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID requestId, String code, String message) {
        requestRepo.findById(requestId).ifPresent(request -> {
            request.fail(code, message, clock.instant());
            requestRepo.saveAndFlush(request);
        });
    }

    private List<String> usedTopics() {
        List<String> topics = new ArrayList<>();
        for (String plan : requestRepo.recentPlans()) {
            JsonNode node = read(plan);
            node.path("listening").forEach(t -> topics.add(t.asText()));
            node.path("reading").forEach(t -> topics.add(t.asText()));
            for (String field : List.of("writing_task1", "writing_task2", "speaking")) {
                if (node.hasNonNull(field)) {
                    topics.add(node.get(field).asText());
                }
            }
        }
        return topics;
    }

    private JsonNode read(String json) {
        try {
            return json == null ? mapper.createArrayNode() : mapper.readTree(json);
        } catch (JsonProcessingException unreadable) {
            return mapper.createObjectNode();
        }
    }
}
