package com.englow3.assessment.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.assessment.api.SubmittedAssessmentContextReader;
import com.englow3.assessment.repository.AssessmentAttemptRepository;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.shared.error.*;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmittedAssessmentContextReaderImpl implements SubmittedAssessmentContextReader {
    private final AssessmentAttemptRepository attempts;
    private final UserDirectory users;
    private final ObjectMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Review read(UUID attemptId) {
        UUID user = users.requireCurrentUserId();
        var attempt = attempts.findById(attemptId).filter(a -> a.getUserId().equals(user))
                .orElseThrow(() -> new NotFoundException("ASSESSMENT_NOT_FOUND", "Submission not found"));
        if (attempt.getStatus() != AssessmentAttemptStatus.COMPLETED)
            throw new ConflictException("TUTOR_CONTEXT_NOT_READY", "This submission has not been assessed yet");
        try {
            var task = mapper.readTree(attempt.getTaskSnapshot());
            var report = mapper.readTree(attempt.getReport());
            if (task == null || !task.isObject() || report == null || !report.isObject())
                throw new ConflictException("TUTOR_CONTEXT_UNAVAILABLE", "The stored assessment cannot be read");
            StringBuilder feedback = new StringBuilder();
            for (String key : java.util.List.of("summary", "strengths", "improvements", "nextSteps")) {
                JsonNode value = report.path(key);
                if (value.isTextual())
                    feedback.append(key).append(": ").append(value.asText()).append('\n');
                else if (value.isArray())
                    for (JsonNode item : value)
                        if (item.isTextual())
                            feedback.append(item.asText()).append('\n');
            }
            for (JsonNode criterion : report.path("criteria")) {
                feedback.append(criterion.path("key").asText()).append(": ").append(criterion.path("feedback").asText())
                        .append('\n');
            }
            Double overall = report.path("overall").isNumber() ? report.path("overall").asDouble() : null;
            if (overall != null && (!Double.isFinite(overall) || overall < 0 || overall > 9))
                overall = null;
            return new Review(attemptId, attempt.getSkill().name(), clip(task.path("title").asText(), 160),
                    clip(task.path("instructions").asText(), 1200),
                    clip(attempt.getSkill() == AssessmentSkill.SPEAKING ? attempt.getRecognizedText()
                            : attempt.getAnswerText(), 1800),
                    clip(feedback.toString(), 1400), overall, attempt.getSource(), attempt.getAssessedAt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new ConflictException("TUTOR_CONTEXT_UNAVAILABLE", "The stored assessment cannot be read");
        }
    }

    private static String clip(String text, int max) {
        if (text == null)
            return "";
        return text.codePointCount(0, text.length()) <= max ? text
                : text.substring(0, text.offsetByCodePoints(0, max)) + " [truncated]";
    }
}
