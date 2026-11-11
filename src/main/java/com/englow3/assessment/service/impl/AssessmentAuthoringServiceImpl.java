package com.englow3.assessment.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.assessment.api.AssessmentAnswerGraded;
import com.englow3.assessment.api.AssessmentTaskArchiving;
import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.dto.result.AssessmentAttemptResult;
import com.englow3.assessment.dto.result.AssessmentReviewResult;
import com.englow3.assessment.dto.result.AssessmentSubmissionSummary;
import com.englow3.assessment.dto.result.AssessmentTaskResult;
import com.englow3.assessment.dto.result.AssessmentWorkloadResult;
import com.englow3.assessment.entity.AssessmentAttempt;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentReview;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.assessment.entity.AssessmentTask;
import com.englow3.assessment.entity.AssessmentTaskStatus;
import com.englow3.assessment.helper.AssessmentEvidence;
import com.englow3.assessment.helper.AssessmentResultMapper;
import com.englow3.assessment.helper.AssessmentRubric;
import com.englow3.assessment.query.AssessmentQueueQuery;
import com.englow3.assessment.query.AssessmentWorkloadQuery;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.englow3.assessment.repository.AssessmentReviewRepository;
import com.englow3.assessment.repository.AssessmentTaskRepository;
import com.englow3.assessment.service.AssessmentAuthoringService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssessmentAuthoringServiceImpl implements AssessmentAuthoringService {
    private final AssessmentTaskRepository taskRepo;
    private final AssessmentAttemptRepository attemptRepo;
    private final AssessmentReviewRepository reviewRepo;
    private final AssessmentWorkloadQuery workloadQuery;
    private final AssessmentQueueQuery queueQuery;
    private final UserDirectory users;
    private final CurrentUser currentUser;
    private final AssessmentResultMapper resultMapper;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final com.englow3.assessment.helper.TaskImages images;
    private final ApplicationEventPublisher events;
    private final com.englow3.assessment.repository.AssessmentAssignmentRepository assignmentRepo;

    public List<UserDirectory.StaffMember> reviewers() {
        requireAdmin();
        return users.backOfficeUsers();
    }

    public com.englow3.assessment.dto.result.AssessmentAssignmentResult assignment(UUID id) {
        UUID actor = users.requireCurrentUserId();
        return transactionTemplate.execute(tx -> assignmentResult(reviewable(id, actor, false)));
    }

    public com.englow3.assessment.dto.result.AssessmentAssignmentResult assign(UUID id, UUID reviewerId, long version,
            String note) {
        requireAdmin();
        if (note == null || note.isBlank() || note.strip().length() > 2000)
            throw new BadRequestException("ASSIGNMENT_NOTE_REQUIRED",
                    "Explain the assignment in at most 2000 characters");
        if (reviewerId != null && !users.isBackOfficeUser(reviewerId))
            throw new BadRequestException("REVIEWER_NOT_AVAILABLE", "Choose an active staff member or administrator");
        UUID actor = users.requireCurrentUserId();
        return transactionTemplate.execute(tx -> {
            AssessmentAttempt a = reviewable(id, actor);
            UUID previous = a.getAssignedReviewerId();
            a.assignReviewer(reviewerId, version);
            if (!java.util.Objects.equals(previous, reviewerId)) {
                assignmentRepo.save(com.englow3.assessment.entity.AssessmentAssignment.of(id, actor, reviewerId,
                        note.strip(), clock.instant()));
                attemptRepo.saveAndFlush(a);
            }
            return assignmentResult(a);
        });
    }

    private com.englow3.assessment.dto.result.AssessmentAssignmentResult assignmentResult(AssessmentAttempt a) {
        String name = a.getAssignedReviewerId() == null ? null
                : users.displayNames(List.of(a.getAssignedReviewerId())).get(a.getAssignedReviewerId());
        return new com.englow3.assessment.dto.result.AssessmentAssignmentResult(a.getId(), a.getAssignedReviewerId(),
                name, a.getVersion(),
                assignmentRepo.findTop20ByAttemptIdOrderByCreatedAtDesc(a.getId()).stream()
                        .map(e -> new com.englow3.assessment.dto.result.AssessmentAssignmentResult.Entry(e.getId(),
                                e.getAssignedByUserId(), e.getReviewerId(), e.getNote(), e.getCreatedAt()))
                        .toList());
    }

    public AssessmentWorkloadResult workload() {
        return workloadQuery.counts(currentUser.hasRole("ADMIN") ? null : users.requireCurrentUserId());
    }

    public Page<AssessmentTaskResult> tasks(AssessmentSkill skill, AssessmentTaskStatus status, Pageable page) {
        UUID author = currentUser.hasRole("ADMIN") ? null : users.requireCurrentUserId();
        return transactionTemplate
                .execute(tx -> taskRepo.search(skill, status, author, null, page).map(t -> signedFrom(t, true)));
    }

    public AssessmentTaskResult create(AssessmentTaskCommand c) {
        UUID author = users.requireCurrentUserId();
        return transactionTemplate
                .execute(tx -> signedFrom(taskRepo.saveAndFlush(AssessmentTask.draft(c, author)), true));
    }

    public AssessmentTaskResult taskDetail(UUID id) {
        UUID author = users.requireCurrentUserId();
        return transactionTemplate.execute(tx -> signedFrom(task(id, author, false), true));
    }

    public List<AssessmentReviewResult> reviews(UUID id) {
        UUID author = users.requireCurrentUserId();
        List<AssessmentReview> reviews = transactionTemplate.execute(tx -> {
            reviewable(id, author, false);
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
            if (task.getVersion() != version) {
                throw new ConflictException("TASK_CHANGED", "The task changed. Reload before editing");
            }
            task.edit(c);
            return signedFrom(taskRepo.saveAndFlush(task), true);
        });
    }

    /**
     * Checked before the upload, so a task that cannot take a chart costs no storage, and again when attaching, in case
     * it changed meanwhile. The upload itself runs outside any transaction.
     */
    public AssessmentTaskResult attachImage(UUID id, org.springframework.web.multipart.MultipartFile image) {
        UUID author = users.requireCurrentUserId();
        transactionTemplate.executeWithoutResult(tx -> task(id, author).requireImageable());
        String key = images.upload(id, image);
        return transactionTemplate.execute(tx -> {
            AssessmentTask task = task(id, author);
            task.attachImage(key);
            return signedFrom(taskRepo.saveAndFlush(task), true);
        });
    }

    public AssessmentTaskResult removeImage(UUID id) {
        UUID author = users.requireCurrentUserId();
        return transactionTemplate.execute(tx -> {
            AssessmentTask task = task(id, author);
            task.removeImage();
            return signedFrom(taskRepo.saveAndFlush(task), true);
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
                    events.publishEvent(new AssessmentTaskArchiving(id));
                    task.archive();
                }
                case "restore" -> {
                    requireAdmin();
                    task.restore();
                }
                default -> throw new BadRequestException("TASK_ACTION_INVALID", "Unknown task action");
            }
            return signedFrom(taskRepo.saveAndFlush(task), true);
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
        AssessmentAttempt a = transactionTemplate.execute(tx -> reviewable(id, author, false));
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
            // Staff can also sit tasks, including their own; marking that work would be awarding themselves a grade.
            if (attempt.getUserId().equals(author)) {
                throw new ForbiddenException("REVIEW_OWN_SUBMISSION", "A reviewer cannot grade their own submission");
            }
            if (version != null && version != attempt.getVersion()) {
                throw new ConflictException("GRADE_CHANGED",
                        "Another reviewer changed this submission. Reload before publishing");
            }
            String normalized = AssessmentRubric.validate(mapper, attempt.getSkill(), report);
            AssessmentEvidence.validate(mapper, attempt.getSkill(), normalized, attempt.getAnswerText(),
                    attempt.getAudioContentLength());
            if (note == null || note.isBlank()) {
                throw new BadRequestException("REVIEW_NOTE_REQUIRED", "Explain the assessment");
            }
            String previous = attempt.getReport();
            attempt.finishHuman(normalized, transcript == null ? attempt.getRecognizedText() : transcript,
                    clock.instant(), currentUser.hasRole("ADMIN"));
            reviewRepo.save(AssessmentReview.of(id, author, previous, normalized, note.strip()));
            events.publishEvent(new AssessmentAnswerGraded(attempt.getId(), attempt.getUserId(), attempt.getTaskId(),
                    attempt.getSkill().name()));
            return attemptRepo.saveAndFlush(attempt);
        });
        return resultMapper.from(a, true);
    }

    private AssessmentAttempt reviewable(UUID id, UUID author) {
        return reviewable(id, author, true);
    }

    private AssessmentAttempt reviewable(UUID id, UUID author, boolean lock) {
        AssessmentAttempt a = (lock ? attemptRepo.lockById(id) : attemptRepo.findById(id))
                .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Submission not found"));
        if (!currentUser.hasRole("ADMIN")) {
            if (a.getAssignedReviewerId() == null)
                task(a.getTaskId(), author, lock);
            else if (!a.getAssignedReviewerId().equals(author))
                throw new ForbiddenException("SUBMISSION_NOT_ASSIGNED",
                        "This submission is assigned to another reviewer");
        }
        if (a.getStatus() == AssessmentAttemptStatus.DRAFT) {
            throw new NotFoundException("ATTEMPT_NOT_FOUND", "Submission not found");
        }
        return a;
    }

    private AssessmentTask task(UUID id, UUID author) {
        return task(id, author, true);
    }

    private AssessmentTask task(UUID id, UUID author, boolean lock) {
        AssessmentTask task = (lock ? taskRepo.lockById(id) : taskRepo.findById(id))
                .orElseThrow(() -> new NotFoundException("TASK_NOT_FOUND", "Task not found"));
        if (!currentUser.hasRole("ADMIN") && !task.getCreatedByUserId().equals(author)) {
            throw new ForbiddenException("TASK_NOT_OWNED", "Staff can only manage their own tasks");
        }
        return task;
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN")) {
            throw new ForbiddenException("ADMIN_REQUIRED", "Only administrators can review or archive tasks");
        }
    }

    /** A task as a screen shows it, its chart signed. Not for snapshots: those keep the key only. */
    private AssessmentTaskResult signedFrom(com.englow3.assessment.entity.AssessmentTask task, boolean authoring) {
        return images.signed(AssessmentTaskResult.from(task, authoring));
    }
}
