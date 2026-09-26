package com.englow3.quiz.service.impl;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.quiz.dto.command.CreateQuizCommand;
import com.englow3.quiz.dto.result.ContentReviewResult;
import com.englow3.quiz.dto.result.QuizSummaryResult;
import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.quiz.service.AdminQuizService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Quiz metadata and review lifecycle. */
@Service
@RequiredArgsConstructor
public class AdminQuizServiceImpl implements AdminQuizService {

    private final QuizRepository quizRepo;
    private final QuizQuestionRepository questionRepo;
    private final UserDirectory userDirectory;
    private final Clock clock;

    @Transactional
    public QuizSummaryResult create(CreateQuizCommand command) {
        if (quizRepo.existsBySlug(command.slug())) {
            throw new ConflictException("QUIZ_SLUG_TAKEN", "A quiz already uses the slug %s".formatted(command.slug()));
        }

        Quiz quiz = quizRepo.save(Quiz.draft(command.slug(), command.title(), command.description(), command.category(),
                command.targetLevel(), command.timeLimitSeconds(), command.passingScorePercent(),
                userDirectory.requireCurrentUserId()));
        return summaryOf(quiz);
    }

    @Transactional(readOnly = true)
    public Page<ContentReviewResult> searchForAuthoring(QuizStatus status, String title, Pageable pageable) {
        Page<Quiz> page = quizRepo.searchForAuthoring(status, title, pageable);
        Map<UUID, Long> counts = questionRepo.countByQuizIds(page.getContent().stream().map(Quiz::getId).toList());
        return page.map(quiz -> ContentReviewResult.of(quiz, counts.getOrDefault(quiz.getId(), 0L)));
    }

    @Transactional
    public ContentReviewResult publish(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.publish(questionRepo.countByQuizId(quizId), questionRepo.sumPoints(quizId), clock.instant());
        return reviewStateOf(quiz);
    }

    @Transactional
    public ContentReviewResult submitForReview(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.submitForReview(questionRepo.countByQuizId(quizId), questionRepo.sumPoints(quizId), clock.instant());
        return reviewStateOf(quiz);
    }

    @Transactional
    public ContentReviewResult approve(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.approve(userDirectory.requireCurrentUserId(), questionRepo.countByQuizId(quizId),
                questionRepo.sumPoints(quizId), clock.instant());
        return reviewStateOf(quiz);
    }

    @Transactional
    public ContentReviewResult reject(UUID quizId, String note) {
        Quiz quiz = requireQuiz(quizId);
        quiz.reject(userDirectory.requireCurrentUserId(), note, clock.instant());
        return reviewStateOf(quiz);
    }

    @Transactional
    public ContentReviewResult archive(UUID quizId) {
        Quiz quiz = requireQuiz(quizId);
        quiz.archive();
        return reviewStateOf(quiz);
    }

    private ContentReviewResult reviewStateOf(Quiz quiz) {
        return ContentReviewResult.of(quiz, questionRepo.countByQuizId(quiz.getId()));
    }

    private QuizSummaryResult summaryOf(Quiz quiz) {
        return QuizSummaryResult.of(quiz, questionRepo.countByQuizId(quiz.getId()), null, 0);
    }

    private Quiz requireQuiz(UUID quizId) {
        return quizRepo.findById(quizId)
                .orElseThrow(() -> new NotFoundException("QUIZ_NOT_FOUND", "No quiz with id %s".formatted(quizId)));
    }
}
