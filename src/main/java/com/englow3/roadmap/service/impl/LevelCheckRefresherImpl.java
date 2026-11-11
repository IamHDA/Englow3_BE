package com.englow3.roadmap.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.englow3.assessment.api.SpeakingLevelReader;
import com.englow3.exam.api.ScoredExamAttemptReader;
import com.englow3.exam.api.ScoredExamAttemptReader.Sitting;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.LevelCheck;
import com.englow3.roadmap.entity.LevelCheckPurpose;
import com.englow3.roadmap.entity.LevelCheckStatus;
import com.englow3.roadmap.helper.LevelCombiner;
import com.englow3.roadmap.query.LevelBandQuery;
import com.englow3.roadmap.query.LevelCheckWeightQuery;
import com.englow3.roadmap.service.LevelCheckRefresher;
import com.englow3.roadmap.service.RoadmapProgression;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.PlacementRecorder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LevelCheckRefresherImpl implements LevelCheckRefresher {

    /** The paper's vocabulary and grammar questions sit in its READING section. */
    static final String VOCABULARY_SECTION = "READING";
    static final String LISTENING_SECTION = "LISTENING";

    private final ScoredExamAttemptReader sittings;
    private final SpeakingLevelReader speaking;
    private final LevelBandQuery bands;
    private final LevelCheckWeightQuery weightQuery;
    private final LearnerGoalReader goalReader;
    private final PlacementRecorder placementRecorder;
    private final RoadmapProgression progression;
    private final Clock clock;

    /**
     * Reads each part's grade from the module that owns it, records what has arrived, and finishes the check once it
     * can.
     */
    @Override
    public Outcome refresh(LevelCheck check) {
        if (check.getStatus() == LevelCheckStatus.COMPLETED) {
            return new Outcome(false, null);
        }
        UUID userId = check.getUserId();
        // The learner sits the paper and answers the task on their usual screens; the sitting and the answer made
        // since this check began are this check's, unless the learner named others explicitly.
        if (check.getExamAttemptId() == null) {
            sittings.latestFullSitting(check.getExamId(), userId, check.getCreatedAt())
                    .ifPresent(sitting -> check.attachExamAttempt(sitting.attemptId()));
        }
        if (check.getSpeakingAttemptId() == null && check.getSpeakingTaskId() != null && !check.isSpeakingSkipped()) {
            speaking.latestAnswer(check.getSpeakingTaskId(), userId, check.getCreatedAt())
                    .ifPresent(answer -> check.attachSpeakingAttempt(answer.attemptId()));
        }
        if (check.getExamAttemptId() != null && !check.hasWrittenParts()) {
            sittings.read(check.getExamAttemptId(), userId).filter(sitting -> sitting.scored() && sitting.full())
                    .ifPresent(sitting -> check.recordWrittenParts(band(sitting, VOCABULARY_SECTION),
                            band(sitting, LISTENING_SECTION)));
        }
        boolean speakingFailed = false;
        if (check.getSpeakingAttemptId() != null && check.getSpeakingLevel() == null) {
            var answer = speaking.read(check.getSpeakingAttemptId(), userId).orElse(null);
            if (answer != null && answer.completed()) {
                CefrLevel.parse(answer.level()).ifPresent(check::recordSpeaking);
            }
            speakingFailed = answer != null && answer.failed();
        }
        if (!check.hasWrittenParts()) {
            return new Outcome(speakingFailed, null);
        }
        // The weights in force now; the check keeps them beside its result, so a later change rewrites nothing.
        LevelCombiner.Weights weights = weightQuery
                .weightsFor(goalReader.read(userId).map(LearnerGoalReader.Goal::purposeCodes).orElse(List.of()),
                        clock.instant())
                .orElse(LevelCombiner.FALLBACK);
        if (!check.isReadyToComplete()) {
            CefrLevel provisional = LevelCombiner
                    .combine(check.getVocabLevel(), check.getListeningLevel(), null, weights).level();
            return new Outcome(speakingFailed, provisional);
        }
        LevelCombiner.Result result = LevelCombiner.combine(check.getVocabLevel(), check.getListeningLevel(),
                check.getSpeakingLevel(), weights);
        check.complete(result.level(), result.appliedWeights(), clock.instant());
        // The learner's level is what they were last measured at, whichever test measured it.
        placementRecorder.recordLevelCheck(userId, check.getId(), result.level().name(), check.getCreatedAt());
        if (check.getPurpose() == LevelCheckPurpose.MILESTONE) {
            progression.find(userId, check.getMilestoneId()).ifPresent(context -> {
                context.milestone().recordLevelCheck(check.getId(), result.level(), clock.instant());
                progression.settle(context);
            });
        }
        return new Outcome(false, null);
    }

    /** A section's share of its points as a band; a paper without that section is judged on its overall share. */
    private CefrLevel band(Sitting sitting, String section) {
        BigDecimal percent = sitting.sections().stream().filter(score -> section.equals(score.sectionType()))
                .filter(score -> score.max() != null && score.max().signum() > 0).findFirst()
                .map(score -> score.awarded().multiply(BigDecimal.valueOf(100)).divide(score.max(), 2,
                        RoundingMode.DOWN))
                .orElse(sitting.scorePercentage() == null ? BigDecimal.ZERO : sitting.scorePercentage());
        return bands.band(section, percent, sitting.scoredAt() == null ? clock.instant() : sitting.scoredAt())
                .orElse(CefrLevel.A1);
    }
}
