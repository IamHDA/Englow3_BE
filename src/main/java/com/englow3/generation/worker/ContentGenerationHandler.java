package com.englow3.generation.worker;

import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.GenerationClient;
import com.englow3.ai.client.LlmException;
import com.englow3.assessment.api.GeneratedTaskWriter;
import com.englow3.exam.api.GeneratedPaperWriter;
import com.englow3.generation.service.GenerationArtifacts;
import com.englow3.generation.service.impl.GenerationSteps;
import com.englow3.generation.service.impl.GenerationSteps.Work;
import com.englow3.shared.error.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs one step of a generation request per job.
 * <p>
 * {@link #run} does the step's outside work - the adapter call and storage uploads - and returns what happened. Draft
 * creation and review submission use GenerationArtifacts to commit their database changes and step checkpoint
 * atomically. Other steps are recorded by {@link #complete} inside the queue's transaction. A step whose job is late or
 * repeated finds the request elsewhere and does nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentGenerationHandler implements AiJobHandler {

    private final GenerationSteps steps;
    private final GenerationClient client;
    private final GeneratedPaperWriter papers;
    private final GeneratedTaskWriter tasks;
    private final ObjectMapper mapper;
    private final GenerationArtifacts artifacts;

    @Override
    public String handles() {
        return "CONTENT_GENERATION";
    }

    @Override
    public Outcome run(UUID jobId, UUID requestId, String inputPayload) {
        try {
            var payload = mapper.readTree(inputPayload);
            String step = payload.path("step").asText();
            Work work = steps.work(requestId, step);
            if (work == null || work.attempt() != payload.path("attempt").asInt()) {
                return Outcome.succeeded("{\"stale\":true}");
            }
            return Outcome.succeeded(work.writes() ? write(work) : switch (step) {
                case "assemble" -> assemble(work);
                case "chart" -> chart(work);
                case "finish" -> finish(work);
                default -> record(work);
            });
        } catch (LlmException e) {
            return e.isRetryable()
                    ? Outcome.transientFailure(e.getCode(), "The AI service is unavailable")
                            .retryAfter(e.getRetryAfter())
                    : Outcome.permanentFailure(e.getCode(), "The AI service refused the step");
        } catch (DomainException e) {
            return Outcome.permanentFailure(e.getCode(), e.getMessage());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Outcome.permanentFailure("GENERATION_RESULT_UNREADABLE", "A step's result could not be read");
        }
    }

    @Override
    public void complete(UUID requestId, String inputPayload, String outputPayload) {
        try {
            var payload = mapper.readTree(inputPayload);
            steps.apply(requestId, payload.path("step").asText(), payload.path("attempt").asInt(), outputPayload);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalArgumentException("Invalid generation job payload", unreadable);
        }
    }

    @Override
    public void onGaveUp(UUID requestId, String inputPayload, String errorCode) {
        try {
            var payload = mapper.readTree(inputPayload);
            steps.failAt(requestId, payload.path("step").asText(), payload.path("attempt").asInt(), errorCode);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalArgumentException("Invalid generation job payload", unreadable);
        }
    }

    /** One model call; the adapter returns the component with any rule problems, which complete() acts on. */
    private String write(Work work) throws JsonProcessingException {
        ObjectNode body = mapper.createObjectNode().put("step", work.step());
        if (!"plan".equals(work.step())) {
            body.set("plan", work.components().path("plan"));
        }
        if (work.brief() != null) {
            body.put("brief", work.brief());
        }
        body.set("used_topics", mapper.valueToTree(work.usedTopics()));
        if (work.retryContext() != null) {
            body.set("previous", work.retryContext());
        }
        return client.step(body.toString());
    }

    /** Builds the set and creates its drafts: the paper and the five tasks, authored by whoever asked. */
    private String assemble(Work work) throws JsonProcessingException {
        ObjectNode body = mapper.createObjectNode();
        body.set("components", work.components());
        JsonNode built = mapper.readTree(client.assemble(body.toString()));
        if (!built.path("problems").isEmpty()) {
            throw new IllegalArgumentException("The set misses question types: " + built.path("problems"));
        }
        artifacts.assemble(work.requestId(), built.toString());
        return "{}";
    }

    /** The Task 1 chart, uploaded and attached to the first task. */
    private String chart(Work work) {
        byte[] png = Base64.getDecoder().decode(work.assembly().path("chart_png_base64").asText());
        UUID task1 = work.taskIds().getFirst();
        tasks.attachImage(task1, tasks.uploadImage(task1, png));
        return "{}";
    }

    /** One Listening part recorded, uploaded and attached to the draft paper. */
    private String record(Work work) throws JsonProcessingException {
        String part = work.step().substring("audio-".length());
        ObjectNode body = mapper.createObjectNode();
        body.set("segments", work.assembly().path("listening").path(part));
        byte[] mp3 = Base64.getDecoder()
                .decode(mapper.readTree(client.audio(body.toString())).path("mp3_base64").asText());
        papers.attachListeningAudio(work.examId(), Integer.parseInt(part), papers.uploadAudio(work.examId(), mp3));
        return "{}";
    }

    /** Everything goes for review, and a draft full test joins the paper and the five tasks. */
    private String finish(Work work) {
        artifacts.finish(work.requestId());
        return "{}";
    }
}
