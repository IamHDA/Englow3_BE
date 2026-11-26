package com.englow3.roadmap.worker;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.englow3.ai.api.AiJobHandler;
import com.englow3.ai.client.LlmClient;
import com.englow3.ai.client.LlmException;
import com.englow3.roadmap.helper.RoadmapPlan.Context;
import com.englow3.roadmap.helper.RoadmapPlan.ProposedStage;
import com.englow3.roadmap.helper.RoadmapPlanValidator;
import com.englow3.roadmap.helper.RoadmapPrompt;
import com.englow3.roadmap.service.RoadmapPlanWriter;
import com.englow3.shared.error.DomainException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

/**
 * Asks the model for a plan and checks it before anything is written. Everything the check needs travels in the job's
 * payload, so {@link #run} touches no database: a plan that breaks a rule is a failed attempt and is asked for again,
 * the way the queue retries any transient failure, until the attempts run out and the roadmap is marked failed.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class RoadmapGenerationHandler implements AiJobHandler {

    static final int MAX_OUTPUT_TOKENS = 6000;

    private final LlmClient llm;
    private final RoadmapPlanWriter writer;
    private final ObjectMapper mapper;

    @Override
    public String handles() {
        return "ROADMAP_GENERATION";
    }

    @Override
    public Outcome run(UUID jobId, UUID targetId, String inputPayload) {
        JsonNode input;
        Context context;
        try {
            input = mapper.readTree(inputPayload);
            if (!targetId.toString().equals(input.path("roadmapId").asString(""))) {
                return Outcome.permanentFailure("ROADMAP_PAYLOAD_INVALID", "Roadmap identity does not match");
            }
            context = mapper.treeToValue(input.path("context"), Context.class);
        } catch (JacksonException | IllegalArgumentException e) {
            return Outcome.permanentFailure("ROADMAP_PAYLOAD_INVALID", "The roadmap request is unreadable");
        }
        try {
            ObjectNode prompt = ((ObjectNode) input.deepCopy());
            prompt.remove("token");
            prompt.remove("roadmapId");
            String raw = llm.generateStructured(RoadmapPrompt.SYSTEM, mapper.writeValueAsString(prompt),
                    MAX_OUTPUT_TOKENS);
            JsonNode response = mapper.readTree(raw);
            JsonNode proposal = mapper.readTree(response.path("content").asString(""));
            List<ProposedStage> stages = RoadmapPlanValidator.validate(proposal, context);
            ObjectNode result = mapper.createObjectNode();
            result.set("stages", mapper.valueToTree(stages));
            result.put("model", response.path("model").asString(null));
            return Outcome.succeeded(result.toString());
        } catch (LlmException e) {
            return e.isRetryable()
                    ? Outcome.transientFailure(e.getCode(), "The planning provider is unavailable")
                            .retryAfter(e.getRetryAfter())
                    : Outcome.permanentFailure(e.getCode(), "The planning provider rejected the request");
        } catch (DomainException e) {
            // A plan that breaks a rule: the model may well get it right on another go.
            return Outcome.transientFailure(e.getCode(), e.getMessage());
        } catch (JacksonException | IllegalArgumentException e) {
            return Outcome.transientFailure("ROADMAP_PLAN_INVALID", "The proposed roadmap is not valid JSON");
        }
    }

    @Override
    public void complete(UUID targetId, String inputPayload, String outputPayload) {
        try {
            JsonNode input = mapper.readTree(inputPayload);
            JsonNode output = mapper.readTree(outputPayload);
            List<ProposedStage> stages = mapper.readerForListOf(ProposedStage.class).readValue(output.path("stages"));
            writer.apply(targetId, UUID.fromString(input.path("token").asString("")), stages,
                    output.path("model").isString() ? output.path("model").asString("") : null, RoadmapPrompt.VERSION);
        } catch (JacksonException invalid) {
            throw new IllegalArgumentException("Invalid roadmap completion", invalid);
        }
    }

    @Override
    public void onGaveUp(UUID targetId, String errorCode) {
        writer.giveUp(targetId, errorCode);
    }
}
