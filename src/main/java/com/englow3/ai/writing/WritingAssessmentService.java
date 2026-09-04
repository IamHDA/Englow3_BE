package com.englow3.ai.writing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.ai.foundation.RenderedPrompt;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class WritingAssessmentService {

    private static final int MAX_WORDS = 2000;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final ObjectMapper objectMapper;

    WritingAssessmentService(JdbcTemplate jdbcTemplate, UserRepository userRepository, CurrentUser currentUser,
            AiPromptService promptService, AiJobService jobService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.promptService = promptService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    List<WritingDtos.TaskSummary> tasks() {
        return jdbcTemplate.query("""
                select task_id, task_type, prompt, min_words, max_words, rubric_ref
                from writing_tasks
                where review_status in ('human_approved', 'human_verified', 'approved', 'published')
                order by task_type, task_id
                """,
                (rs, row) -> new WritingDtos.TaskSummary(rs.getString("task_id"), rs.getString("task_type"),
                        rs.getString("prompt"), rs.getObject("min_words", Integer.class),
                        rs.getObject("max_words", Integer.class), rs.getString("rubric_ref")));
    }

    @Transactional
    WritingDtos.SubmissionAccepted submit(WritingDtos.CreateSubmissionRequest request) {
        User user = requireUser();
        String response = request.responseText().strip();
        if (response.isEmpty()) {
            throw new BadRequestException("WRITING_RESPONSE_REQUIRED", "Writing response is required");
        }
        int wordCount = countWords(response);
        if (wordCount > MAX_WORDS) {
            throw new BadRequestException("WRITING_RESPONSE_TOO_LONG",
                    "Writing responses cannot exceed " + MAX_WORDS + " words");
        }

        ExistingSubmission existing = existing(user.getId(), request.idempotencyKey());
        if (existing != null) {
            if (!existing.taskId().equals(request.taskId()) || !existing.responseText().equals(response)) {
                throw new ConflictException("WRITING_IDEMPOTENCY_CONFLICT",
                        "The idempotency key was already used for a different writing submission");
            }
            return new WritingDtos.SubmissionAccepted(existing.id(), existing.jobId(), existing.status(),
                    existing.wordCount());
        }

        Task task = task(request.taskId());
        List<RubricDimension> dimensions = dimensions(task.rubricId());
        if (dimensions.isEmpty()) {
            throw new ConflictException("WRITING_RUBRIC_EMPTY", "The writing task rubric has no dimensions");
        }
        String rubric = dimensions.stream().map(dimension -> "%s (weight %s): %s".formatted(dimension.name(),
                dimension.weight(), dimension.bandDescriptors())).reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        RenderedPrompt prompt = promptService.render("WRITING_ASSESSMENT",
                Map.of("task", task.prompt(), "rubric", rubric, "response", response));

        UUID submissionId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into writing_submissions
                    (id, user_id, task_id, rubric_id, response_text, word_count, status,
                     prompt_version, idempotency_key)
                values (?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?)
                """, submissionId, user.getId(), task.taskId(), task.rubricId(), response, wordCount, prompt.version(),
                request.idempotencyKey());

        ObjectNode payload = objectMapper.createObjectNode().put("submissionId", submissionId.toString())
                .put("systemPrompt", prompt.systemPrompt()).put("userPrompt", prompt.userPrompt())
                .put("learnerResponse", response);
        ArrayNode criteria = payload.putArray("criteria");
        dimensions.forEach(
                dimension -> criteria.addObject().put("name", dimension.name()).put("weight", dimension.weight()));
        AiJob job = jobService.submitForCurrentUser(AiCapability.WRITING, "WRITING_ASSESSMENT", "WRITING_SUBMISSION",
                submissionId, prompt.version(), payload, "WRITING:" + request.idempotencyKey());
        jdbcTemplate.update("update writing_submissions set ai_job_id = ? where id = ?", job.getId(), submissionId);
        return new WritingDtos.SubmissionAccepted(submissionId, job.getId(), job.getStatus().name(), wordCount);
    }

    @Transactional(readOnly = true)
    List<WritingDtos.SubmissionSummary> history() {
        UUID userId = requireUser().getId();
        return jdbcTemplate.query("""
                select s.id, s.task_id, s.status, s.word_count, a.overall_score, a.cefr_level,
                       s.created_at, s.completed_at
                from writing_submissions s
                left join writing_assessments a on a.submission_id = s.id
                where s.user_id = ? order by s.created_at desc limit 100
                """,
                (rs, row) -> new WritingDtos.SubmissionSummary(rs.getObject("id", UUID.class), rs.getString("task_id"),
                        rs.getString("status"), rs.getInt("word_count"), rs.getBigDecimal("overall_score"),
                        rs.getString("cefr_level"), instant(rs, "created_at"), instant(rs, "completed_at")),
                userId);
    }

    @Transactional(readOnly = true)
    WritingDtos.SubmissionResult result(UUID submissionId) {
        UUID userId = requireUser().getId();
        WritingDtos.SubmissionResult result = jdbcTemplate.query("""
                select s.id, s.task_id, s.status, s.word_count, s.ai_job_id, s.created_at, s.completed_at,
                       a.overall_score, a.cefr_level, a.summary, a.criterion_scores, a.strengths,
                       a.improvements, a.corrected_response, a.sample_revision
                from writing_submissions s
                left join writing_assessments a on a.submission_id = s.id
                where s.id = ? and s.user_id = ?
                """, rs -> rs.next() ? mapResult(rs) : null, submissionId, userId);
        if (result == null) {
            throw new NotFoundException("WRITING_SUBMISSION_NOT_FOUND", "Writing submission was not found");
        }
        return result;
    }

    private WritingDtos.SubmissionResult mapResult(ResultSet rs) throws SQLException {
        WritingDtos.Assessment assessment = rs.getBigDecimal("overall_score") == null ? null
                : new WritingDtos.Assessment(rs.getBigDecimal("overall_score"), rs.getString("cefr_level"),
                        rs.getString("summary"), json(rs.getString("criterion_scores")),
                        strings(rs.getString("strengths")), strings(rs.getString("improvements")),
                        rs.getString("corrected_response"), rs.getString("sample_revision"));
        return new WritingDtos.SubmissionResult(rs.getObject("id", UUID.class), rs.getString("task_id"),
                rs.getString("status"), rs.getInt("word_count"), rs.getObject("ai_job_id", UUID.class), assessment,
                instant(rs, "created_at"), instant(rs, "completed_at"));
    }

    private Task task(String taskId) {
        Task task = jdbcTemplate.query("""
                select task_id, prompt, rubric_ref from writing_tasks
                where task_id = ? and review_status in
                    ('human_approved', 'human_verified', 'approved', 'published')
                """,
                rs -> rs.next() ? new Task(rs.getString("task_id"), rs.getString("prompt"), rs.getString("rubric_ref"))
                        : null,
                taskId);
        if (task == null) {
            throw new NotFoundException("WRITING_TASK_NOT_FOUND", "Approved writing task was not found");
        }
        return task;
    }

    private List<RubricDimension> dimensions(String rubricId) {
        return jdbcTemplate.query("""
                select name, weight, band_descriptors from rubric_dimensions
                where rubric_id = ? order by name
                """, (rs, row) -> new RubricDimension(rs.getString("name"), rs.getBigDecimal("weight"),
                rs.getString("band_descriptors")), rubricId);
    }

    private ExistingSubmission existing(UUID userId, String key) {
        return jdbcTemplate.query("""
                select id, task_id, response_text, word_count, status, ai_job_id
                from writing_submissions where user_id = ? and idempotency_key = ?
                """,
                rs -> rs.next()
                        ? new ExistingSubmission(rs.getObject("id", UUID.class), rs.getString("task_id"),
                                rs.getString("response_text"), rs.getInt("word_count"), rs.getString("status"),
                                rs.getObject("ai_job_id", UUID.class))
                        : null,
                userId, key);
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored writing assessment JSON is invalid", ex);
        }
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored writing assessment list is invalid", ex);
        }
    }

    private int countWords(String value) {
        return value.isBlank() ? 0 : value.split("\\s+").length;
    }

    private Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }

    private record Task(String taskId, String prompt, String rubricId) {
    }

    private record RubricDimension(String name, BigDecimal weight, String bandDescriptors) {
    }

    private record ExistingSubmission(UUID id, String taskId, String responseText, int wordCount, String status,
            UUID jobId) {
    }
}
