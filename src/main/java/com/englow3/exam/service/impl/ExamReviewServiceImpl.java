package com.englow3.exam.service.impl;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.exam.dto.command.ApproveExamCommand;
import com.englow3.exam.dto.command.ArchiveExamCommand;
import com.englow3.exam.dto.command.PublishExamCommand;
import com.englow3.exam.dto.command.RejectExamCommand;
import com.englow3.exam.dto.command.SubmitExamForReviewCommand;
import com.englow3.exam.dto.result.ExamResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.service.ExamReviewService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Exam review and publication lifecycle. */
@Service
@RequiredArgsConstructor
public class ExamReviewServiceImpl implements ExamReviewService {

    private final ExamRepository examRepo;
    private final QuestionRepository questionRepo;
    private final UserDirectory userDirectory;
    private final Clock clock;

    @Transactional
    public ExamResult publish(PublishExamCommand command) {
        Exam exam = requireExam(command.examId());
        requireChoiceOnly(exam);
        exam.publish(examRepo.countSections(exam.getId()), examRepo.countQuestions(exam.getId()),
                examRepo.sumSectionScores(exam.getId()), questionRepo.findIncompleteQuestionOrderNos(exam.getId()),
                clock.instant());
        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult submitForReview(SubmitExamForReviewCommand command) {
        Exam exam = requireExam(command.examId());
        requireChoiceOnly(exam);
        exam.submitForReview(examRepo.countSections(exam.getId()), examRepo.countQuestions(exam.getId()),
                examRepo.sumSectionScores(exam.getId()), questionRepo.findIncompleteQuestionOrderNos(exam.getId()),
                clock.instant());
        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult approve(ApproveExamCommand command) {
        Exam exam = requireExam(command.examId());
        requireChoiceOnly(exam);
        exam.approve(userDirectory.requireCurrentUserId(), examRepo.countSections(exam.getId()),
                examRepo.countQuestions(exam.getId()), examRepo.sumSectionScores(exam.getId()),
                questionRepo.findIncompleteQuestionOrderNos(exam.getId()), clock.instant());
        return ExamResult.of(exam);
    }

    /**
     * A paper that reached the database with a Writing or Speaking section (imported, or saved before the editor
     * refused them) must not go live: learners would answer an essay prompt by picking an option.
     */
    private void requireChoiceOnly(Exam exam) {
        if (examRepo.countProductiveSections(exam.getId()) > 0) {
            throw new ConflictException("EXAM_PRODUCTIVE_SECTION",
                    "Writing and Speaking sections cannot be published in a mock exam; use Writing & Speaking tasks");
        }
    }

    @Transactional
    public ExamResult reject(RejectExamCommand command) {
        Exam exam = requireExam(command.examId());
        exam.reject(userDirectory.requireCurrentUserId(), command.note(), clock.instant());
        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult restore(UUID examId) {
        Exam exam = requireExam(examId);
        exam.restore();
        return ExamResult.of(exam);
    }

    @Transactional
    public ExamResult archive(ArchiveExamCommand command) {
        Exam exam = requireExam(command.examId());
        exam.archive();
        return ExamResult.of(exam);
    }

    private Exam requireExam(UUID examId) {
        return examRepo.findById(examId)
                .orElseThrow(() -> new NotFoundException("EXAM_NOT_FOUND", "No exam with id %s".formatted(examId)));
    }
}
