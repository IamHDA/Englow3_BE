package com.englow3.user.service;

import java.util.List;

import com.englow3.user.dto.command.*;
import com.englow3.user.dto.result.*;

public interface OnboardingService {
    OnboardingStateResult currentState();

    List<LearningPurposeResult> listLearningPurposes();

    OnboardingStateResult selectLearningPurposes(SelectLearningPurposesCommand command);

    OnboardingStateResult setCertificateTarget(SetCertificateTargetCommand command);

    OnboardingStateResult setCurrentLevel(SetCurrentLevelCommand command);

    OnboardingStateResult setLearningGoal(SetLearningGoalCommand command);

    OnboardingStateResult selectTargetSkills(SelectTargetSkillsCommand command);

    OnboardingStateResult complete();
}
