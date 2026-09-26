package com.englow3.tutor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.ai.client.LlmClient;
import com.englow3.ai.client.LlmException;
import com.englow3.ai.entity.AiJob;
import com.englow3.ai.entity.AiJobType;
import com.englow3.ai.api.AiJobHandler.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * One queued question. Every branch answers the same two things - is this worth retrying, and does the learner get told
 * - and the two answers are deliberately not the same.
 */
class TutorReplyHandlerTest {

    private final LlmClient llmClient = mock(LlmClient.class);
    private final TutorReplyWriter writer = mock(TutorReplyWriter.class);

    private final TutorReplyHandler handler = new TutorReplyHandler(llmClient, writer, new ObjectMapper());

    private final UUID messageId = UUID.randomUUID();

    private AiJob jobFor(String payload) {
        return AiJob.pending(AiJobType.TUTOR_REPLY, "TUTOR_MESSAGE", messageId, "openai", "gpt-4o-mini",
                TutorPrompt.VERSION, payload, UUID.randomUUID().toString(), (short) 3);
    }

    private AiJob validJob() {
        return jobFor("""
                {"messageId":"%s","systemPrompt":"You are a tutor.","userPrompt":"What is a gerund?"}
                """.formatted(messageId));
    }

    private Outcome run(AiJob job) {
        return handler.run(job.getId(), job.getTargetId(), job.getInputPayload());
    }

    @Test
    void storesTheAnswerAndReportsSuccess() {
        when(llmClient.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn(
                "{\"content\":\"A verb used as a noun.\",\"model\":\"gpt-4o-mini\",\"input_tokens\":120,\"output_tokens\":40}");

        Outcome outcome = run(validJob());

        assertThat(outcome.success()).isTrue();
        verify(writer).storeReply(messageId, "A verb used as a noun.", "gpt-4o-mini", 120, 40);
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * The rule worth pinning down. A retryable failure leaves the turn pending: the learner is still waiting and the
     * answer is still coming, so telling them it failed before a retry that may well succeed is worse than the wait.
     */
    @Test
    void leavesTheLearnerWaitingWhenTheFailureIsWorthRetrying() {
        when(llmClient.generate(anyString(), anyString(), anyDouble(), anyInt()))
                .thenThrow(new LlmException("TUTOR_SERVICE_UNREACHABLE", "timeout", true));

        Outcome outcome = run(validJob());

        assertThat(outcome.retryable()).isTrue();
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * A refusal is reported as final. Telling the learner waits for the queue, which alone knows the job is over - see
     * {@code tellsTheLearnerOnceTheJobHasGivenUp}.
     */
    @Test
    void reportsARefusalAsFinalWithoutTellingTheLearnerYet() {
        when(llmClient.generate(anyString(), anyString(), anyDouble(), anyInt()))
                .thenThrow(new LlmException("TUTOR_GENERATION_REJECTED", "400", false));

        Outcome outcome = run(validJob());

        assertThat(outcome.retryable()).isFalse();
        verify(writer, never()).markFailed(any(), anyString());
    }

    /** The provider answered with something that is not a reply. Asking again gives the same answer. */
    @Test
    void givesUpOnAnAnswerThatCannotBeRead() {
        when(llmClient.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("<html>502</html>");

        Outcome outcome = run(validJob());

        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("TUTOR_REPLY_UNREADABLE");
        verify(writer, never()).markFailed(any(), anyString());
    }

    /** An empty answer is not an answer - storing it would leave the learner reading a blank bubble. */
    @Test
    void failsWhenTheProviderReturnedNothingToShow() {
        when(llmClient.generate(anyString(), anyString(), anyDouble(), anyInt())).thenReturn("{\"content\":\"   \"}");

        Outcome outcome = run(validJob());

        assertThat(outcome.errorCode()).isEqualTo("TUTOR_REPLY_EMPTY");
        verify(writer, never()).markFailed(any(), anyString());
    }

    /**
     * A missing token count is stored as null, not zero. A cost report adding up zeros would quietly under-count rather
     * than showing a gap where the number should be.
     */
    @Test
    void doesNotInventATokenCountThatWasNotReported() {
        when(llmClient.generate(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn("{\"content\":\"A verb used as a noun.\",\"model\":\"gpt-4o-mini\"}");

        run(validJob());

        verify(writer).storeReply(eq(messageId), anyString(), eq("gpt-4o-mini"), eq(null), eq(null));
    }

    /**
     * A payload this handler did not write will never become one, and there is no message id in it to mark failed
     * either - so it ends immediately rather than being retried into the same dead end.
     */
    @Test
    void failsPermanentlyOnAPayloadItDoesNotRecognise() {
        Outcome outcome = run(jobFor("{\"somethingElse\":true}"));

        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("TUTOR_JOB_PAYLOAD_UNREADABLE");
        verify(llmClient, never()).generate(anyString(), anyString(), anyDouble(), anyInt());
    }

    @Test
    void failsPermanentlyOnAPayloadThatIsNotJson() {
        Outcome outcome = run(jobFor("not json at all"));

        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.errorCode()).isEqualTo("TUTOR_JOB_PAYLOAD_UNREADABLE");
    }

    /** The queue dispatches on this; a handler answering for the wrong type would silently take work it cannot do. */
    @Test
    void handlesOnlyTutorReplies() {
        assertThat(handler.handles()).isEqualTo("TUTOR_REPLY");
    }

    /**
     * Telling the learner is not {@code run}'s job any more: it knew about one of the four ways a job ends. The worker
     * calls this once the queue has recorded the job as over, whichever way it got there.
     */
    @Test
    void tellsTheLearnerOnceTheJobHasGivenUp() {
        handler.onGaveUp(validJob().getTargetId(), "PROVIDER_DOWN");

        verify(writer).markFailed(messageId, "PROVIDER_DOWN");
    }

    /**
     * Read from the job's own target, not its payload - so even a job whose payload could not be read still reaches the
     * learner it belongs to. That case used to leave them waiting with no way to be told.
     */
    @Test
    void reachesTheLearnerEvenWhenThePayloadWasUnreadable() {
        handler.onGaveUp(jobFor("not json at all").getTargetId(), "PAYLOAD_UNREADABLE");

        verify(writer).markFailed(messageId, "PAYLOAD_UNREADABLE");
    }
}
