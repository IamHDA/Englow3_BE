package com.englow3.assessment.service;

import java.util.UUID;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.dto.result.*;
import com.englow3.assessment.entity.*;
import org.springframework.data.domain.*;

public interface AssessmentAuthoringService {
    AssessmentWorkloadResult workload();

    Page<AssessmentTaskResult> tasks(AssessmentSkill skill, AssessmentTaskStatus status, Pageable page);

    AssessmentTaskResult create(AssessmentTaskCommand command);

    AssessmentTaskResult taskDetail(UUID id);

    java.util.List<AssessmentReviewResult> reviews(UUID id);

    AssessmentTaskResult edit(UUID id, AssessmentTaskCommand command, long version);

    AssessmentTaskResult transition(UUID id, String action, String note);

    Page<AssessmentAttemptResult> submissions(AssessmentAttemptStatus status, Pageable page);

    Page<AssessmentSubmissionSummary> searchSubmissions(AssessmentAttemptStatus status, AssessmentSkill skill,
            String term, boolean oldest, Pageable page);

    AssessmentAttemptResult submission(UUID id);

    AssessmentAttemptResult grade(UUID id, String report, String note, String transcript);

    AssessmentAttemptResult grade(UUID id, String report, String note, String transcript, Long version);
}
