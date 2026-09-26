package com.englow3.tutor.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.englow3.ai.client.LlmClient;
import com.englow3.ai.client.LlmException;
import com.englow3.ai.api.AiJobHandler;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Runs one queued question: ask {@code ai_service} to generate an answer, write it into the pending turn.
 * <p>
 * Lives in {@code tutor} rather than in {@code ai} because it writes this module's tables. The queue knows only the
 * {@link AiJobHandler} interface, which is why adding this kind of work needed no change to the worker.
 */
@Component
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
@RequiredArgsConstructor
public class TutorReplyHandler implements AiJobHandler {

    private static final Logger log = LoggerFactory.getLogger(TutorReplyHandler.class);

    /**
     * Low, and deliberately so. A learner asking the same grammar question twice should get the same rule back; a tutor
     * that answers differently each time reads as one that is guessing.
     */
    private static final double TEMPERATURE = 0.3;

    /**
     * Enough for an explanation with an example, not enough for an essay. A learner reading on a phone will not finish
     * six paragraphs, and the ceiling is also what stops one question costing an unbounded amount.
     */
    private static final int MAX_OUTPUT_TOKENS = 1_024;

    private final LlmClient llmClient;
    private final TutorReplyWriter writer;
    private final ObjectMapper objectMapper;

    @Override
    public String handles() {
        return "TUTOR_REPLY";
    }

    /**
     * Not transactional, deliberately. Calling the provider is the slow part, and holding a database connection across
     * it is what the queue exists to avoid. The write goes through {@link TutorReplyWriter}, in a transaction that
     * lasts as long as a write.
     */
    @Override
    public Outcome run(UUID jobId, UUID targetId, String inputPayload) {
        JsonNode request = readRequest(jobId, inputPayload);
        if (request == null) {
            return Outcome.permanentFailure("TUTOR_JOB_PAYLOAD_UNREADABLE",
                    "The job payload is not the shape this handler writes");
        }

        UUID messageId = UUID.fromString(request.path("messageId").asText());

        try {
            String response = llmClient.generate(request.path("systemPrompt").asText(),
                    request.path("userPrompt").asText(), TEMPERATURE, MAX_OUTPUT_TOKENS);
            Reply reply = parse(response);
            writer.storeReply(messageId, reply.content(), reply.model(), reply.inputTokens(), reply.outputTokens());

            return Outcome.succeeded(response);
        } catch (LlmException providerFailure) {
            return finish(providerFailure.getCode(), providerFailure.getMessage(), providerFailure.isRetryable());
        } catch (DomainException unusable) {
            // The provider answered with something that is not a usable reply. Asking again produces the same
            // answer, so this ends here rather than three attempts later.
            return finish(unusable.getCode(), unusable.getMessage(), false);
        }
    }

    /**
     * Says what kind of failure this was, and nothing more. Whether the learner is told is decided once the queue has
     * recorded it - see {@link #onGaveUp} - because only the queue knows whether a retry is still coming. Deciding it
     * here covered one of the four ways a job can end and left the turn waiting on the other three.
     */
    private static Outcome finish(String code, String message, boolean retryable) {
        return retryable ? Outcome.transientFailure(code, message) : Outcome.permanentFailure(code, message);
    }

    /**
     * Nothing more is coming, so the learner is told. Read from the job's own target rather than its payload, which is
     * what lets this work even for a payload that could not be read.
     */
    @Override
    public void onGaveUp(UUID targetId, String errorCode) {
        writer.markFailed(targetId, errorCode);
    }

    /** What the adapter returned, reduced to the parts worth keeping on the message. */
    record Reply(String content, String model, Integer inputTokens, Integer outputTokens) {
    }

    /**
     * Reads the adapter's answer. Token counts are optional and a missing one is stored as null rather than zero: a
     * cost report adding up zeros would quietly under-count rather than showing a gap.
     */
    private Reply parse(String response) {
        JsonNode answer;
        try {
            answer = objectMapper.readTree(response);
        } catch (JsonProcessingException malformed) {
            throw new BadRequestException("TUTOR_REPLY_UNREADABLE",
                    "ai_service answered with something that is not JSON");
        }

        String content = answer.path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new BadRequestException("TUTOR_REPLY_EMPTY", "The tutor returned nothing to show");
        }

        return new Reply(content, answer.path("model").asText(llmClient.defaultModel()),
                answer.hasNonNull("input_tokens") ? answer.get("input_tokens").asInt() : null,
                answer.hasNonNull("output_tokens") ? answer.get("output_tokens").asInt() : null);
    }

    private JsonNode readRequest(UUID jobId, String inputPayload) {
        try {
            JsonNode request = objectMapper.readTree(inputPayload);
            return request.hasNonNull("messageId") ? request : null;
        } catch (JsonProcessingException malformed) {
            log.error("AI job {} carries an unreadable payload", jobId, malformed);
            return null;
        }
    }

}
