package com.englow3.assessment.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.dto.result.AssessmentAttemptResult;
import com.englow3.assessment.dto.result.AssessmentReviewResult;
import com.englow3.assessment.dto.result.AssessmentSubmissionSummary;
import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.assessment.dto.result.AssessmentWorkloadResult;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTaskStatus;

public interface AssessmentAuthoringService {
    AssessmentWorkloadResult workload();

    Page<AssessmentTaskResult> tasks(AssessmentSkill skill, AssessmentTaskStatus status, Pageable page);

    AssessmentTaskResult create(AssessmentTaskCommand command);

    AssessmentTaskResult taskDetail(UUID id);

    List<AssessmentReviewResult> reviews(UUID id);

    AssessmentTaskResult edit(UUID id, AssessmentTaskCommand command, long version);

    AssessmentTaskResult transition(UUID id, String action, String note);

    Page<AssessmentAttemptResult> submissions(AssessmentAttemptStatus status, Pageable page);

    Page<AssessmentSubmissionSummary> searchSubmissions(AssessmentAttemptStatus status, AssessmentSkill skill,
            String term, boolean oldest, Pageable page);

    AssessmentAttemptResult submission(UUID id);

    AssessmentAttemptResult grade(UUID id, String report, String note, String transcript);

    AssessmentAttemptResult grade(UUID id, String report, String note, String transcript, Long version);
}
