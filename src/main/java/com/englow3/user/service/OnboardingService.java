package com.englow3.user.service;

import java.math.BigDecimal;
import java.util.List;

import com.englow3.user.dto.command.SelectLearningPurposesCommand;
import com.englow3.user.dto.command.SelectTargetSkillsCommand;
import com.englow3.user.dto.command.SetCertificateTargetCommand;
import com.englow3.user.dto.command.SetCurrentLevelCommand;
import com.englow3.user.dto.command.SetLearningGoalCommand;
import com.englow3.user.dto.result.LearningPurposeResult;
import com.englow3.user.dto.result.OnboardingStateResult;

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
