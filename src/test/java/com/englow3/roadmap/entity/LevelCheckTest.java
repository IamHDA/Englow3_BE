package com.englow3.roadmap.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LevelCheckTest {

    private static final Instant NOW = Instant.parse("2026-11-01T03:00:00Z");

    private static LevelCheck onboarding(UUID speakingTask) {
        return LevelCheck.start(UUID.randomUUID(), UUID.randomUUID(), LevelCheckPurpose.ONBOARDING, null, null,
                UUID.randomUUID(), speakingTask);
    }

    @Test
    void aMilestoneCheckBelongsToAMilestoneAndOnlyThen() {
        assertThatThrownBy(() -> LevelCheck.start(UUID.randomUUID(), UUID.randomUUID(), LevelCheckPurpose.MILESTONE,
                null, null, UUID.randomUUID(), null)).hasFieldOrPropertyWithValue("code",
                        "LEVEL_CHECK_PURPOSE_INVALID");
        assertThatThrownBy(() -> LevelCheck.start(UUID.randomUUID(), UUID.randomUUID(), LevelCheckPurpose.ONBOARDING,
                null, UUID.randomUUID(), UUID.randomUUID(), null)).hasFieldOrPropertyWithValue("code",
                        "LEVEL_CHECK_PURPOSE_INVALID");
    }

    @Test
    void cannotCompleteWithoutTheWrittenPartsOrAnAnswerToTheSpokenOne() {
        LevelCheck check = onboarding(UUID.randomUUID());
        assertThatThrownBy(() -> check.complete(CefrLevel.B1, "{}", NOW)).hasFieldOrPropertyWithValue("code",
                "LEVEL_CHECK_INCOMPLETE");

        check.recordWrittenParts(CefrLevel.B1, CefrLevel.A2);
        assertThat(check.getStatus()).isEqualTo(LevelCheckStatus.AWAITING_SPEAKING);
        assertThatThrownBy(() -> check.complete(CefrLevel.B1, "{}", NOW)).hasFieldOrPropertyWithValue("code",
                "LEVEL_CHECK_INCOMPLETE");

        check.skipSpeaking();
        check.complete(CefrLevel.A2, "{\"vocabulary\":40}", NOW);
        assertThat(check.getStatus()).isEqualTo(LevelCheckStatus.COMPLETED);
        assertThat(check.getWeights()).contains("vocabulary");
    }

    @Test
    void withNoSpokenTaskTheCheckIsTheWrittenPartsAlone() {
        LevelCheck check = onboarding(null);
        assertThat(check.isSpeakingSkipped()).isTrue();
        assertThatThrownBy(() -> check.attachSpeakingAttempt(UUID.randomUUID())).hasFieldOrPropertyWithValue("code",
                "LEVEL_CHECK_NO_SPEAKING");

        check.recordWrittenParts(CefrLevel.B2, CefrLevel.B2);
        assertThat(check.isReadyToComplete()).isTrue();
    }

    @Test
    void theWeightsAreKeptWithTheResult() {
        LevelCheck check = onboarding(null);
        check.recordWrittenParts(CefrLevel.B2, CefrLevel.B2);
        assertThatThrownBy(() -> check.complete(CefrLevel.B2, " ", NOW)).hasFieldOrPropertyWithValue("code",
                "LEVEL_CHECK_RESULT_INVALID");
    }

    @Test
    void aScoredPaperCannotBeSwappedAndAFinishedCheckIsFinal() {
        LevelCheck check = onboarding(UUID.randomUUID());
        UUID sitting = UUID.randomUUID();
        check.attachExamAttempt(UUID.randomUUID());
        check.attachExamAttempt(sitting);
        check.recordWrittenParts(CefrLevel.B1, CefrLevel.B1);

        assertThatThrownBy(() -> check.attachExamAttempt(UUID.randomUUID())).hasFieldOrPropertyWithValue("code",
                "LEVEL_CHECK_PAPER_DONE");

        check.attachSpeakingAttempt(UUID.randomUUID());
        check.recordSpeaking(CefrLevel.B2);
        assertThatThrownBy(check::skipSpeaking).hasFieldOrPropertyWithValue("code", "LEVEL_CHECK_SPEAKING_DONE");
        check.complete(CefrLevel.B1, "{}", NOW);
        assertThatThrownBy(() -> check.attachExamAttempt(sitting)).hasFieldOrPropertyWithValue("code",
                "LEVEL_CHECK_COMPLETED");
    }
}
