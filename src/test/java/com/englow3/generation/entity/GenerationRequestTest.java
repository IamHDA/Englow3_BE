package com.englow3.generation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class GenerationRequestTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private static GenerationRequest fresh() {
        return GenerationRequest.create(GenerationKind.IELTS_FULL_TEST, UUID.randomUUID(), "  science topics  ", NOW);
    }

    @Test
    void startsQueuedAtThePlanWithTheBriefTrimmed() {
        GenerationRequest request = fresh();

        assertThat(request.getStatus()).isEqualTo(GenerationStatus.QUEUED);
        assertThat(request.getStep()).isEqualTo("plan");
        assertThat(request.getBrief()).isEqualTo("science topics");
        assertThat(GenerationRequest.create(GenerationKind.IELTS_FULL_TEST, UUID.randomUUID(), "  ", NOW).getBrief())
                .isNull();
    }

    @Test
    void refusesABriefOverTheLimit() {
        assertThatThrownBy(() -> GenerationRequest.create(GenerationKind.IELTS_FULL_TEST, UUID.randomUUID(),
                "x".repeat(GenerationRequest.MAX_BRIEF_LENGTH + 1), NOW)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void anAcceptedComponentMovesToTheNextStepAndClearsTheLastAttempt() {
        GenerationRequest request = fresh();
        request.recordProblems("plan", "[\"too short\"]", "{}", NOW);

        request.recordWritten("plan", "{\"plan\":{}}", NOW);

        assertThat(request.getStep()).isEqualTo("listening-1");
        assertThat(request.getStatus()).isEqualTo(GenerationStatus.RUNNING);
        assertThat(request.getAttempt()).isZero();
        assertThat(request.getLastProblems()).isNull();
        assertThat(request.getRetryContext()).isNull();
    }

    @Test
    void aStepTheModelKeepsGettingWrongFailsTheRequestOnTheThirdAttempt() {
        GenerationRequest request = fresh();

        assertThat(request.recordProblems("plan", "[\"a\"]", "{}", NOW)).isTrue();
        assertThat(request.recordProblems("plan", "[\"b\"]", "{}", NOW)).isTrue();
        assertThat(request.recordProblems("plan", "[\"c\"]", "{}", NOW)).isFalse();

        assertThat(request.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(request.getFailureCode()).isEqualTo("GENERATION_STEP_FAILED");
        assertThat(request.getFailureMessage()).contains("plan");
        assertThat(request.getStep()).isEqualTo("plan");
        assertThat(request.getLastProblems()).isEqualTo("[\"c\"]");
    }

    @Test
    void aResultForAnotherStepIsRefusedSoALateJobCannotOverwriteLaterWork() {
        GenerationRequest request = fresh();
        request.recordWritten("plan", "{}", NOW);

        assertThat(request.isAt("plan")).isFalse();
        assertThatThrownBy(() -> request.recordWritten("plan", "{}", NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void runsEveryStepInOrderToCompletion() {
        GenerationRequest request = fresh();
        for (String step : GenerationRequest.STEPS) {
            switch (step) {
                case "assemble" -> request.recordAssembled(UUID.randomUUID(), "[]", "{}", NOW);
                case "finish" -> request.complete(UUID.randomUUID(), NOW);
                default -> {
                    if (step.startsWith("chart") || step.startsWith("audio")) {
                        request.recordDone(step, NOW);
                    } else {
                        request.recordWritten(step, "{}", NOW);
                    }
                }
            }
        }

        assertThat(request.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(request.getFullTestId()).isNotNull();
        assertThat(request.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void aFinishedRequestKeepsItsOutcome() {
        GenerationRequest request = fresh();
        request.fail("AI_PROVIDER_HTTP_401", "rejected", NOW);

        request.fail("ANOTHER", "later", NOW);

        assertThat(request.getFailureCode()).isEqualTo("AI_PROVIDER_HTTP_401");
        assertThat(request.isAt("plan")).isFalse();
    }
}
