package com.englow3.tutor.service.impl;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.error.*;
import com.englow3.tutor.dto.command.*;
import com.englow3.tutor.dto.result.*;
import com.englow3.tutor.entity.*;
import com.englow3.tutor.repository.*;
import com.englow3.tutor.service.TutorBackOfficeService;
import com.englow3.user.api.UserDirectory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorBackOfficeServiceImpl implements TutorBackOfficeService {
    private final TutorResourceRepository resourceRepo;
    private final TutorReportRepository reportRepo;
    private final TutorAuditRepository auditRepo;
    private final TutorMessageRepository messageRepo;
    private final TutorReplyVersionRepository versionRepo;
    private final TutorMemoryRepository memories;
    private final UserDirectory users;
    private final CurrentUser currentUser;
    private final Clock clock;

    private void requireStaff() {
        if (!currentUser.hasRole("ADMIN") && !currentUser.hasRole("STAFF"))
            throw new ForbiddenException("ACCESS_DENIED", "Back-office access required");
    }

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN"))
            throw new ForbiddenException("ACCESS_DENIED", "Administrator access required");
    }

    private TutorResource ownResource(UUID id) {
        TutorResource resource = resourceRepo.lockById(id)
                .orElseThrow(() -> new NotFoundException("TUTOR_RESOURCE_NOT_FOUND", "Resource not found"));
        if (!currentUser.hasRole("ADMIN") && !resource.getAuthorId().equals(users.requireCurrentUserId()))
            throw new ForbiddenException("ACCESS_DENIED", "Only the author can change this resource");
        return resource;
    }

    private void audit(UUID target, String action, String note) {
        auditRepo.save(TutorAudit.record(users.requireCurrentUserId(), target, action, note));
    }

    @Transactional(readOnly = true)
    public Page<TutorResourceResult> resources(String status, Pageable page) {
        requireStaff();
        return resourceRepo.search(users.requireCurrentUserId(), currentUser.hasRole("ADMIN"), status, page)
                .map(TutorResourceResult::of);
    }

    @Transactional(readOnly = true)
    public TutorResourceResult resource(UUID id) {
        requireStaff();
        TutorResource resource = resourceRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("TUTOR_RESOURCE_NOT_FOUND", "Resource not found"));
        if (!currentUser.hasRole("ADMIN") && !resource.getAuthorId().equals(users.requireCurrentUserId())
                && !"PUBLISHED".equals(resource.getStatus()))
            throw new NotFoundException("TUTOR_RESOURCE_NOT_FOUND", "Resource not found");
        return TutorResourceResult.of(resource);
    }

    @Transactional
    public TutorResourceResult create(TutorResourceCommand command) {
        requireStaff();
        TutorResource resource = resourceRepo
                .save(TutorResource.draft(command.title(), command.body(), users.requireCurrentUserId()));
        audit(resource.getId(), "RESOURCE_CREATE", null);
        return TutorResourceResult.of(resource);
    }

    @Transactional
    public TutorResourceResult update(UUID id, TutorResourceCommand command) {
        requireStaff();
        TutorResource resource = ownResource(id);
        resource.edit(command.title(), command.body(), command.rowVersion());
        resourceRepo.flush();
        audit(id, "RESOURCE_EDIT", null);
        return TutorResourceResult.of(resource);
    }

    @Transactional
    public TutorResourceResult resourceAction(UUID id, TutorReviewCommand command) {
        requireStaff();
        TutorResource preview = resourceRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("TUTOR_RESOURCE_NOT_FOUND", "Resource not found"));
        resourceRepo.lockById(preview.getDocumentId()).orElseThrow();
        if ("REVISION".equals(command.action())) {
            if (!currentUser.hasRole("ADMIN") && !"PUBLISHED".equals(preview.getStatus())
                    && !preview.getAuthorId().equals(users.requireCurrentUserId()))
                throw new ForbiddenException("ACCESS_DENIED", "Resource unavailable");
            TutorResource draft = resourceRepo.save(preview.revision(users.requireCurrentUserId(),
                    resourceRepo.maxVersion(preview.getDocumentId()) + 1));
            audit(draft.getId(), "RESOURCE_REVISION", null);
            return TutorResourceResult.of(draft);
        }
        TutorResource resource = ownResource(id);
        if ("SUBMIT".equals(command.action()))
            resource.submit();
        else {
            requireAdmin();
            if (("REJECT".equals(command.action()) || "ARCHIVE".equals(command.action()))
                    && (command.note() == null || command.note().isBlank()))
                throw new BadRequestException("TUTOR_REVIEW_NOTE_REQUIRED", "Explain this decision");
            if ("PUBLISH".equals(command.action()))
                resourceRepo.retirePublished(resource.getDocumentId(), id);
            resource.decide(command.action(), command.note());
        }
        resourceRepo.flush();
        audit(id, "RESOURCE_" + command.action(), command.note());
        return TutorResourceResult.of(resource);
    }

    @Transactional(readOnly = true)
    public Page<TutorReportResult> reports(String status, Pageable page) {
        requireStaff();
        return reportRepo.search(users.requireCurrentUserId(), currentUser.hasRole("ADMIN"), status, page)
                .map(report -> TutorReportResult.of(report, false));
    }

    private TutorReport permittedReport(UUID id) {
        TutorReport report = reportRepo.lockById(id)
                .orElseThrow(() -> new NotFoundException("TUTOR_REPORT_NOT_FOUND", "Report not found"));
        if (!currentUser.hasRole("ADMIN") && !users.requireCurrentUserId().equals(report.getAssignedTo()))
            throw new NotFoundException("TUTOR_REPORT_NOT_FOUND", "Report not found");
        return report;
    }

    @Transactional
    public TutorReportResult report(UUID id) {
        requireStaff();
        TutorReport report = permittedReport(id);
        audit(id, "REPORT_VIEW", null);
        return TutorReportResult.of(report, true);
    }

    @Transactional
    public TutorReportResult reportAction(UUID id, TutorReviewCommand command) {
        requireStaff();
        TutorReport report = permittedReport(id);
        switch (command.action()) {
            case "ASSIGN" -> {
                requireAdmin();
                if (command.assignedTo() == null || !users.isBackOfficeUser(command.assignedTo()))
                    throw new BadRequestException("TUTOR_ASSIGNEE_INVALID", "Choose a staff member or administrator");
                report.assign(command.assignedTo());
            }
            case "PROPOSE" -> {
                if (command.note() == null || command.note().isBlank())
                    throw new BadRequestException("TUTOR_REVIEW_NOTE_REQUIRED", "Explain your review");
                report.propose(command.note(), command.correction());
            }
            case "RESOLVE", "DISMISS" -> {
                requireAdmin();
                if (command.note() == null || command.note().isBlank())
                    throw new BadRequestException("TUTOR_REVIEW_NOTE_REQUIRED", "Explain this decision");
                report.resolve("DISMISS".equals(command.action()), command.note(), clock.instant());
            }
            default -> throw new BadRequestException("TUTOR_REPORT_ACTION", "Unknown report action");
        }
        boolean resolved = "RESOLVED".equals(report.getStatus());
        if (resolved && report.getProposedCorrection() != null)
            versionRepo.attachApprovedCorrection(report.getMessageId(), report.getAnswerSnapshot(),
                    report.getProposedCorrection());
        messageRepo.lockById(report.getMessageId())
                .filter(m -> m.getRevision() == report.getRevision()
                        || (resolved && java.util.Objects.equals(m.getContent(), report.getAnswerSnapshot())))
                .ifPresent(m -> m.reviewed(report.getStatus(),
                        resolved && report.getProposedCorrection() != null ? report.getProposedCorrection()
                                : m.getCorrection()));
        if (resolved && report.getProposedCorrection() != null) {
            messageRepo.flush();
            memories.invalidateForMessage(report.getMessageId());
        }
        audit(id, "REPORT_" + command.action(), command.note());
        return TutorReportResult.of(report, true);
    }
}
