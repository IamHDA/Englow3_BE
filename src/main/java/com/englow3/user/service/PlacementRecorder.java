package com.englow3.user.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.user.entity.CertificateLevel;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.OnboardingStep;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Turns a graded placement attempt into a declared level. It lives in {@code user} and is called by {@code exam},
 * keeping the dependency pointing the same way as {@link UserDirectory}. The exam module knows when a paper has been
 * scored; it has no business deciding what a score means for a learner's profile, and the user module has no business
 * reaching into {@code exam_attempts} to find out.
 */
@Service
@RequiredArgsConstructor
public class PlacementRecorder {

    private final UserRepository userRepo;
    private final LearnerProfileRepository learnerProfileRepo;

    /**
     * Percentage floors, highest first. Deliberately not read from {@code score_conversions}: that table converts a raw
     * score into a certificate's own scale (an IELTS band, a TOEIC number) and is keyed by a non-null certificate,
     * while a placement paper may carry none. These bands are a product decision about our own placement test, so they
     * are stated here rather than disguised as a certificate conversion.
     */
    private static final CefrBand[] BANDS = { new CefrBand(new BigDecimal("90"), CertificateLevel.C2),
            new CefrBand(new BigDecimal("75"), CertificateLevel.C1),
            new CefrBand(new BigDecimal("60"), CertificateLevel.B2),
            new CefrBand(new BigDecimal("45"), CertificateLevel.B1),
            new CefrBand(new BigDecimal("25"), CertificateLevel.A2) };

    private static final CertificateLevel LOWEST_LEVEL = CertificateLevel.A1;

    private record CefrBand(BigDecimal minPercentage, CertificateLevel level) {
    }

    /**
     * Writes the measured level and, when the learner is still sitting on the level step, lets onboarding move on.
     * Silent when the user has no row: grading has already succeeded and the attempt is scored either way, so failing
     * here would roll back a finished exam over a profile write. The learner keeps their result and is simply asked for
     * a level again.
     */
    @Transactional
    public void record(UUID userId, UUID attemptId, BigDecimal scorePercentage) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        LearnerProfile profile = learnerProfileRepo.findByUserId(userId)
                .orElseGet(() -> learnerProfileRepo.save(LearnerProfile.forUser(userId)));

        profile.recordPlacement(levelFor(scorePercentage), attemptId);

        // Only the step this test was meant to answer. A learner who has finished onboarding and retakes the placement
        // test gets an updated level, not a reset back through the remaining steps.
        if (user.getOnboardingStep() == OnboardingStep.CURRENT_LEVEL) {
            user.moveTo(OnboardingStep.LEARNING_GOAL);
        }
    }

    static CertificateLevel levelFor(BigDecimal scorePercentage) {
        if (scorePercentage == null) {
            return LOWEST_LEVEL;
        }
        for (CefrBand band : BANDS) {
            if (scorePercentage.compareTo(band.minPercentage()) >= 0) {
                return band.level();
            }
        }
        return LOWEST_LEVEL;
    }
}
