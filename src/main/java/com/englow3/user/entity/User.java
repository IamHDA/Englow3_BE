package com.englow3.user.entity;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.englow3.shared.error.ConflictException;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Rows are created by the Supabase trigger on auth.users, never by this application - so there is no factory here, only
 * reads and updates.
 */
@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    private UUID id;

    @Column(name = "auth_provider_id", nullable = false, updatable = false)
    private UUID authProviderId;

    @Column(nullable = false, updatable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_object_key")
    private String avatarObjectKey;

    @Column(name = "banner_object_key")
    private String bannerObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_step", nullable = false)
    private OnboardingStep onboardingStep;

    @ElementCollection
    @CollectionTable(name = "user_learning_purposes", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "learning_purpose_id", nullable = false)
    private Set<Integer> learningPurposeIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "user_target_skills", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "skill", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<TargetSkill> targetSkills = new HashSet<>();

    protected User() {
    }

    public void changeFullName(String fullName) {
        this.fullName = fullName;
    }

    public void rename(String displayName) {
        this.displayName = displayName;
    }

    public void changeAvatar(String objectKey) {
        this.avatarObjectKey = objectKey;
    }

    public void changeBanner(String objectKey) {
        this.bannerObjectKey = objectKey;
    }

    public void changeGender(Gender gender) {
        this.gender = gender;
    }

    public void changeBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public void selectLearningPurposes(Set<Integer> purposeIds) {
        if (purposeIds.isEmpty()) {
            throw new ConflictException("ONBOARDING_PURPOSE_REQUIRED", "At least one learning purpose is required");
        }
        learningPurposeIds.clear();
        learningPurposeIds.addAll(purposeIds);
    }

    /** An empty set means "I don't know yet" - a legitimate answer. */
    public void selectTargetSkills(Set<TargetSkill> skills) {
        targetSkills.clear();
        targetSkills.addAll(skills);
    }

    /**
     * The step is a resume pointer, not a state machine - the flow branches, so the service decides what comes next.
     */
    public void moveTo(OnboardingStep step) {
        this.onboardingStep = step;
    }

    public void completeOnboarding(CertificateLevel currentLevel, boolean certificatePurposeSelected,
            CertificateType certificateType) {
        if (learningPurposeIds.isEmpty()) {
            throw new ConflictException("ONBOARDING_PURPOSE_REQUIRED", "At least one learning purpose is required");
        }
        if (currentLevel == null) {
            throw new ConflictException("ONBOARDING_LEVEL_REQUIRED",
                    "Current level must be known before finishing onboarding");
        }
        if (certificatePurposeSelected && certificateType == null) {
            throw new ConflictException("ONBOARDING_CERTIFICATE_TARGET_REQUIRED",
                    "A certificate learner must pick which certificate to aim for");
        }
        this.onboardingStep = OnboardingStep.COMPLETED;
    }
}
