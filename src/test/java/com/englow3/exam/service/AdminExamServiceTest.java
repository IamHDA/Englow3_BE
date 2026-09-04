package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.ExamDetailCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.command.UpdateExamCommand;
import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.query.AdminExamPaperQuery;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.AdminAccess;

class AdminExamServiceTest {

    private final ExamRepository examRepo = mock(ExamRepository.class);
    private final AdminExamPaperQuery examPaperQuery = mock(AdminExamPaperQuery.class);
    private final AdminAccess adminAccess = mock(AdminAccess.class);

    private final AdminExamService service = new AdminExamService(examRepo, examPaperQuery, adminAccess);

    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void passTheGate() {
        when(adminAccess.requireAdminId()).thenReturn(adminId);
        when(examRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void stampsTheDraftWithTheAdminsOwnUserId() {
        ExamResult result = service.create(command());

        ArgumentCaptor<Exam> saved = ArgumentCaptor.forClass(Exam.class);
        verify(examRepo).save(saved.capture());
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(adminId);
        assertThat(result.id()).isEqualTo(saved.getValue().getId());
    }

    @Test
    void writesNothingWhenTheGateRefusesTheCreate() {
        gateRefuses();

        assertThatThrownBy(() -> service.create(command())).isInstanceOf(ForbiddenException.class);
        verify(examRepo, never()).save(any());
    }

    @Test
    void readsNothingWhenTheGateRefusesTheSearch() {
        gateRefuses();

        assertThatThrownBy(() -> service.search(new SearchExamCommand(null, null, null), Pageable.unpaged()))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(examRepo);
    }

    @Test
    void passesEveryFilterThroughToTheRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(examRepo.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.search(new SearchExamCommand(ExamStatus.DRAFT, ExamType.MOCK, "toeic"), pageable);

        verify(examRepo).search(ExamStatus.DRAFT, ExamType.MOCK, "toeic", pageable);
    }

    /** The list is one query. Content counts were dropped from it - they read as "ready to publish" and are not. */
    @Test
    void doesNotWeighContentToListPapers() {
        when(examRepo.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(draft())));

        service.search(new SearchExamCommand(null, null, null), PageRequest.of(0, 20));

        verify(examRepo, never()).countQuestions(any());
    }

    @Test
    void handsBackThePaperTheQueryLoaded() {
        Exam exam = draft();
        ExamDetailResult loaded = ExamDetailResult.of(exam, List.of());
        when(examPaperQuery.loadForAdmin(exam.getId())).thenReturn(Optional.of(loaded));

        assertThat(service.detail(new ExamDetailCommand(exam.getId()))).isSameAs(loaded);
    }

    @Test
    void failsToLoadAPaperThatDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(examPaperQuery.loadForAdmin(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(new ExamDetailCommand(missing))).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("EXAM_NOT_FOUND");
    }

    @Test
    void loadsNothingWhenTheGateRefusesTheDetail() {
        gateRefuses();

        assertThatThrownBy(() -> service.detail(new ExamDetailCommand(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(examPaperQuery);
    }

    @Test
    void editsThePaperItWasGiven() {
        Exam exam = draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

        ExamResult result = service.update(new UpdateExamCommand(exam.getId(), "TOEIC Practice Test 2", "Revised",
                ExamType.MOCK, CertificateType.TOEIC, CertificateVariant.LR, TargetLevel.B2, 7200,
                new BigDecimal("200.00"), new BigDecimal("650.0")));

        assertThat(result.title()).isEqualTo("TOEIC Practice Test 2");
        assertThat(result.targetLevel()).isEqualTo(TargetLevel.B2);
        assertThat(result.status()).isEqualTo(ExamStatus.DRAFT);
    }

    @Test
    void failsToEditAPaperThatDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(examRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                new UpdateExamCommand(missing, "x", "y", ExamType.MOCK, null, null, null, 60, BigDecimal.ONE, null)))
                        .isInstanceOf(NotFoundException.class).extracting(e -> ((NotFoundException) e).getCode())
                        .isEqualTo("EXAM_NOT_FOUND");
    }

    @Test
    void publishesWithTheFiguresItReadFromTheRepository() {
        Exam exam = draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepo.countSections(exam.getId())).thenReturn(2L);
        when(examRepo.countQuestions(exam.getId())).thenReturn(200L);
        when(examRepo.sumSectionScores(exam.getId())).thenReturn(new BigDecimal("200.00"));

        ExamResult result = service.publish(new PublishExamCommand(exam.getId()));

        assertThat(result.status()).isEqualTo(ExamStatus.PUBLISHED);
    }

    @Test
    void failsToPublishAPaperThatDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(examRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(new PublishExamCommand(missing))).isInstanceOf(NotFoundException.class)
                .extracting(e -> ((NotFoundException) e).getCode()).isEqualTo("EXAM_NOT_FOUND");
    }

    @Test
    void archivesThePaperItWasGiven() {
        Exam exam = draft();
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThat(service.archive(new ArchiveExamCommand(exam.getId())).status()).isEqualTo(ExamStatus.ARCHIVED);
    }

    @Test
    void touchesNothingWhenTheGateRefusesThePublish() {
        gateRefuses();

        assertThatThrownBy(() -> service.publish(new PublishExamCommand(UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(examRepo);
    }

    /** Which callers the gate lets through is AdminAccessTest's business - here it only has to run before the repo. */
    private void gateRefuses() {
        when(adminAccess.requireAdminId()).thenThrow(new ForbiddenException("ADMIN_ONLY", "no"));
    }

    private static Exam draft() {
        CreateExamCommand command = command();
        return Exam.draft(command.title(), command.description(), command.examType(), command.certificateType(),
                command.certificateVariant(), command.targetLevel(), command.durationSeconds(), command.maxRawScore(),
                command.passScore(), UUID.randomUUID());
    }

    private static CreateExamCommand command() {
        return new CreateExamCommand("TOEIC Practice Test 1", "Two skills, seven parts", ExamType.MOCK,
                CertificateType.TOEIC, CertificateVariant.LR, TargetLevel.B1, 7200, new BigDecimal("200.00"),
                new BigDecimal("600.0"));
    }
}
