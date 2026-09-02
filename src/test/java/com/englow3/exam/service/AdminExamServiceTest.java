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
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.SearchExamCommand;
import com.englow3.exam.dto.result.ExamListItemResult;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.TargetLevel;
import com.englow3.exam.repository.ExamContentTotals;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.AdminAccess;

class AdminExamServiceTest {

    private final ExamRepository examRepo = mock(ExamRepository.class);
    private final AdminAccess adminAccess = mock(AdminAccess.class);

    private final AdminExamService service = new AdminExamService(examRepo, adminAccess);

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

    @Test
    void hangsTheContentCountsOffEachRowOfThePage() {
        Exam exam = draft();
        ExamContentTotals totals = totals(exam.getId(), 2, 200);
        when(examRepo.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(exam)));
        when(examRepo.contentTotals(List.of(exam.getId()))).thenReturn(List.of(totals));

        ExamListItemResult row = service.search(new SearchExamCommand(null, null, null), PageRequest.of(0, 20))
                .getContent().getFirst();

        assertThat(row.sectionCount()).isEqualTo(2);
        assertThat(row.questionCount()).isEqualTo(200);
    }

    /** {@code in ()} is not valid SQL, so an empty page must never reach the totals query. */
    @Test
    void doesNotAskForTotalsOfAnEmptyPage() {
        when(examRepo.search(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.search(new SearchExamCommand(null, null, null), PageRequest.of(0, 20));

        verify(examRepo, never()).contentTotals(any());
    }

    @Test
    void publishesWithTheTotalsItReadFromTheRepository() {
        Exam exam = draft();
        ExamContentTotals totals = totals(exam.getId(), 2, 200);
        when(examRepo.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepo.contentTotals(List.of(exam.getId()))).thenReturn(List.of(totals));

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

    private static ExamContentTotals totals(UUID examId, long sectionCount, long questionCount) {
        ExamContentTotals totals = mock(ExamContentTotals.class);
        when(totals.getExamId()).thenReturn(examId);
        when(totals.getSectionCount()).thenReturn(sectionCount);
        when(totals.getQuestionCount()).thenReturn(questionCount);
        when(totals.getSectionsRawTotal()).thenReturn(new BigDecimal("200.00"));
        return totals;
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
