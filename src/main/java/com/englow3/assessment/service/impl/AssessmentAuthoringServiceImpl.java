package com.englow3.assessment.service.impl;

import java.time.Clock;
import java.util.*;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.dto.result.*;
import com.englow3.assessment.entity.*;
import com.englow3.assessment.helper.AssessmentRubric;
import com.englow3.assessment.repository.*;
import com.englow3.assessment.service.AssessmentAuthoringService;
import com.englow3.shared.error.*;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssessmentAuthoringServiceImpl implements AssessmentAuthoringService {
    private final AssessmentTaskRepository taskRepo;
    private final AssessmentAttemptRepository attemptRepo;
    private final AssessmentReviewRepository reviewRepo;
    private final com.englow3.assessment.query.AssessmentWorkloadQuery workloadQuery;
    private final com.englow3.assessment.query.AssessmentQueueQuery queueQuery;
    private final UserDirectory users;
    private final CurrentUser currentUser;
    private final com.englow3.assessment.helper.AssessmentResultMapper resultMapper;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public AssessmentWorkloadResult workload() {
        return workloadQuery.counts(currentUser.hasRole("ADMIN") ? null : users.requireCurrentUserId());
    }

    public Page<AssessmentTaskResult> tasks(AssessmentSkill skill, AssessmentTaskStatus status, Pageable page) {
        UUID author = currentUser.hasRole("ADMIN") ? null : users.requireCurrentUserId();
        return transactionTemplate.execute(
                tx -> taskRepo.search(skill, status, author, page).map(t -> AssessmentTaskResult.from(t, true)));
    }

    public AssessmentTaskResult create(AssessmentTaskCommand c) {
        UUID author = users.requireCurrentUserId();
        return transactionTemplate
                .execute(tx -> AssessmentTaskResult.from(taskRepo.saveAndFlush(AssessmentTask.draft(c, author)), true));
    }

    public AssessmentTaskResult taskDetail(UUID id) {
        UUID author = users.requireCurrentUserId();
        return transactionTemplate.execute(tx -> AssessmentTaskResult.from(task(id, author), true));
    }

    public List<AssessmentReviewResult> reviews(UUID id) {
        UUID author = users.requireCurrentUserId();
        List<AssessmentReview> reviews = transactionTemplate.execute(tx -> {
            reviewable(id, author);
            return reviewRepo.findByAttemptIdOrderByCreatedAtDesc(id);
        });
        Map<UUID, String> names = users
                .displayNames(reviews.stream().map(AssessmentReview::getReviewedByUserId).toList());
        return reviews.stream()
                .map(r -> new AssessmentReviewResult(r.getId(), r.getReviewedByUserId(),
                        names.get(r.getReviewedByUserId()), r.getPreviousReport(), r.getReport(), r.getNote(),
                        r.getCreatedAt()))
                .toList();
    }

    public AssessmentTaskResult edit(UUID id, AssessmentTaskCommand c, long version) {
        UUID author = users.requireCurrentUserId();
        return transactionTemplate.execute(tx -> {
            AssessmentTask task = task(id, author);
            if (task.getVersion() != version)
                throw new ConflictException("TASK_CHANGED", "The task changed. Reload before editing");
            task.edit(c);
            return AssessmentTaskResult.from(taskRepo.saveAndFlush(task), true);
        });
    }

    public AssessmentTaskResult transition(UUID id, String action, String note) {
        UUID author = users.requireCurrentUserId();
        return transactionTemplate.execute(tx -> {
            AssessmentTask task = task(id, author);
            switch (action) {
                case "submit" -> task.submit();
                case "approve" -> {
                    requireAdmin();
                    task.approve(author, clock.instant());
                }
                case "reject" -> {
                    requireAdmin();
                    task.reject(author, note);
                }
                case "archive" -> {
                    requireAdmin();
                    task.archive();
                }
                case "restore" -> {
                    requireAdmin();
                    task.restore();
                }
                default -> throw new BadRequestException("TASK_ACTION_INVALID", "Unknown task action");
            }
            return AssessmentTaskResult.from(taskRepo.saveAndFlush(task), true);
        });
    }

    public Page<AssessmentAttemptResult> submissions(AssessmentAttemptStatus status, Pageable page) {
        UUID author = currentUser.hasRole("ADMIN") ? null : users.requireCurrentUserId();
        Page<AssessmentAttempt> attempts = transactionTemplate
                .execute(tx -> attemptRepo.reviewQueue(author, status, page));
        Map<UUID, String> names = users.displayNames(attempts.stream().map(AssessmentAttempt::getUserId).toList());
        return attempts.map(a -> resultMapper.from(a, true, names.get(a.getUserId())));
    }

    public AssessmentAttemptResult submission(UUID id) {
        UUID author = users.requireCurrentUserId();
        AssessmentAttempt a = transactionTemplate.execute(tx -> reviewable(id, author));
        return resultMapper.from(a, true, users.displayNames(List.of(a.getUserId())).get(a.getUserId()));
    }

    public Page<AssessmentSubmissionSummary> searchSubmissions(AssessmentAttemptStatus status, AssessmentSkill skill,
            String term, boolean oldest, Pageable page) {
        UUID author = currentUser.hasRole("ADMIN") ? null : users.requireCurrentUserId();
        return queueQuery.search(author, status, skill, term, oldest, page);
    }

    public AssessmentAttemptResult grade(UUID id, String report, String note, String transcript) {
        return grade(id, report, note, transcript, null);
    }

    public AssessmentAttemptResult grade(UUID id, String report, String note, String transcript, Long version) {
        UUID author = users.requireCurrentUserId();
        AssessmentAttempt a = transactionTemplate.execute(tx -> {
            AssessmentAttempt attempt = reviewable(id, author);
            if (version != null && version != attempt.getVersion())
                throw new ConflictException("GRADE_CHANGED",
                        "Another reviewer changed this submission. Reload before publishing");
            String normalized = AssessmentRubric.validate(mapper, attempt.getSkill(), report);
            com.englow3.assessment.helper.AssessmentEvidence.validate(mapper, attempt.getSkill(), normalized,
                    attempt.getAnswerText(), attempt.getAudioContentLength());
            if (note == null || note.isBlank())
                throw new BadRequestException("REVIEW_NOTE_REQUIRED", "Explain the assessment");
            String previous = attempt.getReport();
            attempt.finishHuman(normalized, transcript == null ? attempt.getRecognizedText() : transcript,
                    clock.instant(), currentUser.hasRole("ADMIN"));
            reviewRepo.save(AssessmentReview.of(id, author, previous, normalized, note.strip()));
            return attemptRepo.saveAndFlush(attempt);
        });
        return resultMapper.from(a, true);
    }

    private AssessmentAttempt reviewable(UUID id, UUID author) {
        AssessmentAttempt a = attemptRepo.lockById(id)
                .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Submission not found"));
        task(a.getTaskId(), author);
        if (a.getStatus() == AssessmentAttemptStatus.DRAFT)
            throw new NotFoundException("ATTEMPT_NOT_FOUND", "Submission not found");
        return a;
    }

    private AssessmentTask task(UUID id, UUID author) {
        AssessmentTask task = taskRepo.lockById(id)
                .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Task not found"));
        if (!currentUser.hasRole("ADMIN") && !task.getCreatedByUserId().equals(author))
            throw new ForbiddenException("TASK_NOT_OWNED", "Staff can only manage their own tasks");
        return task;
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN"))
            throw new ForbiddenException("ADMIN_REQUIRED", "Only administrators can review or archive tasks");
    }
}
