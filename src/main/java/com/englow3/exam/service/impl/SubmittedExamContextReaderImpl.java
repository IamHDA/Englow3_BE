package com.englow3.exam.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.exam.api.SubmittedExamContextReader;
import com.englow3.exam.repository.ExamAttemptRepository;
import com.englow3.exam.repository.QuestionRepository;
import com.englow3.exam.repository.QuestionOptionRepository;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.service.ExamAttemptService;
import com.englow3.shared.error.*;
import com.englow3.user.api.UserDirectory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmittedExamContextReaderImpl implements SubmittedExamContextReader {
    private final ExamAttemptRepository attempts;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final com.englow3.exam.repository.QuestionSetRepository sets;
    private final com.englow3.exam.repository.SectionPartRepository parts;
    private final ExamAttemptService exams;
    private final UserDirectory users;

    @Override
    @Transactional
    public Question read(UUID attemptId, UUID questionId) {
        UUID user = users.requireCurrentUserId();
        var attempt = attempts.findById(attemptId).filter(a -> a.getUserId().equals(user))
                .orElseThrow(() -> new NotFoundException("ATTEMPT_NOT_FOUND", "Exam attempt not found"));
        // Do not call result() for an active attempt: it may finalize an expired exam.
        if (attempt.getStatus() != ExamAttemptStatus.SCORED)
            throw new ConflictException("TUTOR_CONTEXT_NOT_READY", "This exam has not been scored yet");
        var review = exams.result(attemptId).questions().stream().filter(q -> q.questionId().equals(questionId))
                .findFirst().orElseThrow(() -> new NotFoundException("TUTOR_CONTEXT_QUESTION",
                        "Question not in this completed attempt"));
        var question = questions.findById(questionId)
                .orElseThrow(() -> new NotFoundException("TUTOR_CONTEXT_QUESTION", "Question unavailable"));
        var choices = options.findAllById(java.util.stream.Stream
                .concat(review.selectedOptionIds().stream(), review.correctOptionIds().stream()).distinct().toList());
        var set = sets.findById(question.getQuestionSetId())
                .orElseThrow(() -> new NotFoundException("TUTOR_CONTEXT_QUESTION", "Question stimulus unavailable"));
        var part = parts.findById(set.getSectionPartId())
                .orElseThrow(() -> new NotFoundException("TUTOR_CONTEXT_QUESTION", "Question part unavailable"));
        String selected = choices.stream()
                .filter(o -> o.getQuestionId().equals(questionId) && review.selectedOptionIds().contains(o.getId()))
                .map(o -> o.getContent()).collect(java.util.stream.Collectors.joining("; "));
        String correct = choices.stream()
                .filter(o -> o.getQuestionId().equals(questionId) && review.correctOptionIds().contains(o.getId()))
                .map(o -> o.getContent()).collect(java.util.stream.Collectors.joining("; "));
        String passage = clip(set.getContent(), 1400) + "\n" + clip(part.getContent(), 400);
        String instruction = clip(part.getInstruction(), 200) + "\n" + clip(set.getInstruction(), 200);
        return new Question(attemptId, questionId, clip(question.getContent(), 1000), passage, instruction,
                clip(selected, 500), clip(correct, 500), clip(review.explanation(), 800), review.correct(),
                review.awardedRawScore(), question.getMaxRawScore(),
                set.getAudioObjectKey() != null || part.getAudioObjectKey() != null,
                set.getImageObjectKey() != null || part.getImageObjectKey() != null);
    }

    private static String clip(String text, int max) {
        if (text == null)
            return "";
        return text.codePointCount(0, text.length()) <= max ? text
                : text.substring(0, text.offsetByCodePoints(0, max)) + " [truncated]";
    }
}
