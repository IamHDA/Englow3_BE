package com.englow3.ai.placement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class PlacementService {

    private static final int SCORING_POLICY_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final ObjectMapper objectMapper;

    PlacementService(JdbcTemplate jdbcTemplate, UserRepository userRepository, CurrentUser currentUser,
            AiPromptService promptService, AiJobService jobService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.promptService = promptService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    List<PlacementDtos.ExamSummary> availableExams() {
        return jdbcTemplate.query("""
                select e.id, e.title, e.description, e.duration_seconds, count(q.id) as question_count
                from exams e
                join exam_sections es on es.exam_id = e.id
                join section_parts sp on sp.exam_section_id = es.id
                join question_sets qs on qs.section_part_id = sp.id
                join questions q on q.question_set_id = qs.id
                where e.exam_type = 'PLACEMENT' and e.status = 'PUBLISHED'
                group by e.id, e.title, e.description, e.duration_seconds
                order by e.title
                """, (rs, row) -> new PlacementDtos.ExamSummary(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("description"), rs.getInt("duration_seconds"), rs.getInt("question_count")));
    }

    @Transactional
    PlacementDtos.StartAttemptResponse start(UUID examId) {
        User user = requireUser();
        Integer activeAttempts = jdbcTemplate.queryForObject("""
                select count(*) from exam_attempts
                where user_id = ? and exam_id = ? and status = 'IN_PROGRESS' and expires_at > now()
                """, Integer.class, user.getId(), examId);
        if (activeAttempts != null && activeAttempts > 0) {
            throw new ConflictException("PLACEMENT_ATTEMPT_ACTIVE",
                    "An active placement attempt already exists for this exam");
        }
        ExamDefinition exam = jdbcTemplate.query("""
                select e.id, e.version_number, e.duration_seconds, e.max_raw_score, count(q.id) as question_count
                from exams e
                join exam_sections es on es.exam_id = e.id
                join section_parts sp on sp.exam_section_id = es.id
                join question_sets qs on qs.section_part_id = sp.id
                join questions q on q.question_set_id = qs.id
                where e.id = ? and e.exam_type = 'PLACEMENT' and e.status = 'PUBLISHED'
                group by e.id, e.version_number, e.duration_seconds, e.max_raw_score
                """, rs -> rs.next() ? mapExam(rs) : null, examId);
        if (exam == null) {
            throw new NotFoundException("PLACEMENT_EXAM_NOT_FOUND", "Published placement exam was not found");
        }
        UUID attemptId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        Instant expiresAt = startedAt.plusSeconds(exam.durationSeconds());
        jdbcTemplate.update("""
                insert into exam_attempts
                    (id, exam_id, exam_version_number, user_id, status, started_at, expires_at,
                     max_raw_score, question_count)
                values (?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, ?)
                """, attemptId, examId, exam.version(), user.getId(), Timestamp.from(startedAt),
                Timestamp.from(expiresAt), exam.maxScore(), exam.questionCount());
        return new PlacementDtos.StartAttemptResponse(attemptId, expiresAt, exam.questionCount());
    }

    @Transactional(readOnly = true)
    List<PlacementDtos.QuestionResponse> questions(UUID attemptId) {
        UUID userId = requireUser().getId();
        List<QuestionRow> rows = jdbcTemplate.query("""
                select q.id as question_id, q.content as question_content, q.skill_type, q.order_no,
                       qo.id as option_id, qo.content as option_content, qo.order_no as option_order
                from exam_attempts a
                join exam_sections es on es.exam_id = a.exam_id
                join section_parts sp on sp.exam_section_id = es.id
                join question_sets qs on qs.section_part_id = sp.id
                join questions q on q.question_set_id = qs.id
                join question_options qo on qo.question_id = q.id
                where a.id = ? and a.user_id = ?
                order by es.order_no, sp.order_no, qs.order_no, q.order_no, qo.order_no
                """,
                (rs, row) -> new QuestionRow(rs.getObject("question_id", UUID.class), rs.getString("question_content"),
                        rs.getString("skill_type"), rs.getInt("order_no"), rs.getObject("option_id", UUID.class),
                        rs.getString("option_content"), rs.getInt("option_order")),
                attemptId, userId);
        if (rows.isEmpty()) {
            throw new NotFoundException("PLACEMENT_ATTEMPT_NOT_FOUND", "Placement attempt was not found");
        }
        Map<UUID, QuestionBuilder> grouped = new LinkedHashMap<>();
        rows.forEach(
                row -> grouped
                        .computeIfAbsent(row.questionId(),
                                ignored -> new QuestionBuilder(row.questionId(), row.content(), row.skill(),
                                        row.order()))
                        .options()
                        .add(new PlacementDtos.OptionResponse(row.optionId(), row.optionContent(), row.optionOrder())));
        return grouped.values().stream().map(QuestionBuilder::response).toList();
    }

    @Transactional
    void answer(UUID attemptId, PlacementDtos.SubmitAnswerRequest request) {
        UUID userId = requireUser().getId();
        Integer valid = jdbcTemplate.queryForObject("""
                select count(*)
                from exam_attempts a
                join exam_sections es on es.exam_id = a.exam_id
                join section_parts sp on sp.exam_section_id = es.id
                join question_sets qs on qs.section_part_id = sp.id
                join questions q on q.question_set_id = qs.id
                join question_options qo on qo.question_id = q.id
                where a.id = ? and a.user_id = ? and a.status = 'IN_PROGRESS' and a.expires_at > now()
                  and q.id = ? and qo.id = ?
                """, Integer.class, attemptId, userId, request.questionId(), request.optionId());
        if (valid == null || valid == 0) {
            throw new ConflictException("PLACEMENT_ANSWER_NOT_ALLOWED",
                    "The option does not belong to an active placement attempt");
        }

        UUID answerId = jdbcTemplate.query("""
                select id from attempt_answers where exam_attempt_id = ? and question_id = ?
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, attemptId, request.questionId());
        if (answerId == null) {
            answerId = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into attempt_answers
                        (id, exam_attempt_id, question_id, grading_status, answered_at)
                    values (?, ?, ?, 'PENDING', now())
                    """, answerId, attemptId, request.questionId());
        } else {
            jdbcTemplate.update("delete from attempt_answer_options where attempt_answer_id = ?", answerId);
            jdbcTemplate.update("""
                    update attempt_answers
                    set is_correct = null, awarded_raw_score = null, grading_status = 'PENDING', answered_at = now()
                    where id = ?
                    """, answerId);
        }
        jdbcTemplate.update("""
                insert into attempt_answer_options (id, attempt_answer_id, question_option_id)
                values (?, ?, ?)
                """, UUID.randomUUID(), answerId, request.optionId());
    }

    @Transactional
    PlacementDtos.SubmitAttemptResponse submit(UUID attemptId, String idempotencyKey) {
        User user = requireUser();
        AttemptState state = jdbcTemplate.query("""
                select status, expires_at from exam_attempts where id = ? and user_id = ? for update
                """,
                rs -> rs.next() ? new AttemptState(rs.getString("status"), rs.getTimestamp("expires_at").toInstant())
                        : null,
                attemptId, user.getId());
        if (state == null) {
            throw new NotFoundException("PLACEMENT_ATTEMPT_NOT_FOUND", "Placement attempt was not found");
        }
        if ("SCORED".equals(state.status())) {
            return existingSubmission(attemptId);
        }
        if (!"IN_PROGRESS".equals(state.status())) {
            throw new ConflictException("PLACEMENT_ALREADY_SUBMITTED", "Placement attempt is already submitted");
        }
        if (state.expiresAt().isBefore(Instant.now())) {
            jdbcTemplate.update("update exam_attempts set status = 'EXPIRED' where id = ?", attemptId);
            throw new ConflictException("PLACEMENT_ATTEMPT_EXPIRED", "Placement attempt has expired");
        }

        gradeAnswers(attemptId);
        Score score = calculateScore(attemptId);
        BigDecimal percentage = score.maxScore().signum() == 0 ? BigDecimal.ZERO
                : score.rawScore().multiply(BigDecimal.valueOf(100)).divide(score.maxScore(), 2, RoundingMode.HALF_UP);
        String level = assessedLevel(percentage);
        jdbcTemplate.update("""
                update exam_attempts
                set status = 'SCORED', submitted_at = now(), scored_at = now(), raw_score = ?,
                    max_raw_score = ?, score_percentage = ?, correct_answer_count = ?, assessed_level = ?
                where id = ?
                """, score.rawScore(), score.maxScore(), percentage, score.correctCount(), level, attemptId);
        jdbcTemplate.update("""
                insert into learner_profiles (id, user_id, placement_attempt_id, current_level)
                values (?, ?, ?, ?)
                on conflict (user_id) do update
                set placement_attempt_id = excluded.placement_attempt_id,
                    current_level = excluded.current_level
                """, UUID.randomUUID(), user.getId(), attemptId, level);

        if (!jobService.isEnabled(AiCapability.PLACEMENT)) {
            return new PlacementDtos.SubmitAttemptResponse(result(attemptId), null, null);
        }

        String skills = skillSummary(attemptId);
        RenderedPrompt prompt = promptService.render("PLACEMENT_REPORT", Map.of("level", level, "score",
                score.rawScore(), "maxScore", score.maxScore(), "percentage", percentage, "skills", skills));
        UUID reportId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into placement_ai_reports
                    (id, exam_attempt_id, scoring_policy_version, prompt_version)
                values (?, ?, ?, ?)
                """, reportId, attemptId, SCORING_POLICY_VERSION, prompt.version());
        ObjectNode payload = objectMapper.createObjectNode().put("reportId", reportId.toString())
                .put("systemPrompt", prompt.systemPrompt()).put("userPrompt", prompt.userPrompt());
        AiJob job = jobService.submitForCurrentUser(AiCapability.PLACEMENT, "PLACEMENT_REPORT", "PLACEMENT_REPORT",
                reportId, prompt.version(), payload, "PLACEMENT:" + idempotencyKey);
        jdbcTemplate.update("update placement_ai_reports set ai_job_id = ? where id = ?", job.getId(), reportId);
        return new PlacementDtos.SubmitAttemptResponse(result(attemptId), reportId, job.getId());
    }

    @Transactional(readOnly = true)
    PlacementDtos.AttemptResult result(UUID attemptId) {
        UUID userId = requireUser().getId();
        PlacementDtos.AttemptResult result = jdbcTemplate.query("""
                select a.id, a.status, a.raw_score, a.max_raw_score, a.score_percentage, a.assessed_level,
                       r.ai_job_id, r.summary, r.strengths, r.learning_gaps
                from exam_attempts a
                left join placement_ai_reports r on r.exam_attempt_id = a.id
                where a.id = ? and a.user_id = ?
                """, rs -> rs.next() ? mapResult(rs) : null, attemptId, userId);
        if (result == null) {
            throw new NotFoundException("PLACEMENT_ATTEMPT_NOT_FOUND", "Placement attempt was not found");
        }
        return result;
    }

    private PlacementDtos.SubmitAttemptResponse existingSubmission(UUID attemptId) {
        ReportRef report = jdbcTemplate.query("""
                select id, ai_job_id from placement_ai_reports where exam_attempt_id = ?
                """,
                rs -> rs.next() ? new ReportRef(rs.getObject("id", UUID.class), rs.getObject("ai_job_id", UUID.class))
                        : null,
                attemptId);
        if (report == null) {
            return new PlacementDtos.SubmitAttemptResponse(result(attemptId), null, null);
        }
        return new PlacementDtos.SubmitAttemptResponse(result(attemptId), report.id(), report.jobId());
    }

    private void gradeAnswers(UUID attemptId) {
        jdbcTemplate.update("""
                update attempt_answers aa
                set is_correct = exists (
                        select 1 from attempt_answer_options aao
                        join question_options qo on qo.id = aao.question_option_id
                        where aao.attempt_answer_id = aa.id and qo.is_correct
                    ),
                    awarded_raw_score = case when exists (
                        select 1 from attempt_answer_options aao
                        join question_options qo on qo.id = aao.question_option_id
                        where aao.attempt_answer_id = aa.id and qo.is_correct
                    ) then (select max_raw_score from questions where id = aa.question_id) else 0 end,
                    grading_status = 'GRADED', graded_at = now()
                where aa.exam_attempt_id = ?
                """, attemptId);
    }

    private Score calculateScore(UUID attemptId) {
        return jdbcTemplate.query("""
                select coalesce(sum(case when aa.is_correct then q.max_raw_score else 0 end), 0) raw_score,
                       coalesce(sum(q.max_raw_score), 0) max_score,
                       count(*) filter (where aa.is_correct) correct_count
                from exam_attempts a
                join exam_sections es on es.exam_id = a.exam_id
                join section_parts sp on sp.exam_section_id = es.id
                join question_sets qs on qs.section_part_id = sp.id
                join questions q on q.question_set_id = qs.id
                left join attempt_answers aa on aa.exam_attempt_id = a.id and aa.question_id = q.id
                where a.id = ?
                """, rs -> {
            rs.next();
            return new Score(rs.getBigDecimal("raw_score"), rs.getBigDecimal("max_score"), rs.getInt("correct_count"));
        }, attemptId);
    }

    private String assessedLevel(BigDecimal percentage) {
        return jdbcTemplate.queryForObject("""
                select cefr_level from placement_scoring_bands
                where policy_version = ? and minimum_percentage <= ?
                order by minimum_percentage desc limit 1
                """, String.class, SCORING_POLICY_VERSION, percentage);
    }

    private String skillSummary(UUID attemptId) {
        List<String> rows = jdbcTemplate.query("""
                select q.skill_type, count(*) question_count,
                       count(*) filter (where aa.is_correct) correct_count
                from exam_attempts a
                join exam_sections es on es.exam_id = a.exam_id
                join section_parts sp on sp.exam_section_id = es.id
                join question_sets qs on qs.section_part_id = sp.id
                join questions q on q.question_set_id = qs.id
                left join attempt_answers aa on aa.exam_attempt_id = a.id and aa.question_id = q.id
                where a.id = ? group by q.skill_type order by q.skill_type
                """, (rs, row) -> "%s: %d/%d".formatted(rs.getString("skill_type"), rs.getInt("correct_count"),
                rs.getInt("question_count")), attemptId);
        return String.join("\n", rows);
    }

    private ExamDefinition mapExam(ResultSet rs) throws SQLException {
        return new ExamDefinition(rs.getInt("version_number"), rs.getInt("duration_seconds"),
                rs.getBigDecimal("max_raw_score"), rs.getInt("question_count"));
    }

    private PlacementDtos.AttemptResult mapResult(ResultSet rs) throws SQLException {
        return new PlacementDtos.AttemptResult(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getBigDecimal("raw_score"), rs.getBigDecimal("max_raw_score"), rs.getBigDecimal("score_percentage"),
                rs.getString("assessed_level"), rs.getObject("ai_job_id", UUID.class), rs.getString("summary"),
                jsonStrings(rs.getString("strengths")), jsonStrings(rs.getString("learning_gaps")));
    }

    private List<String> jsonStrings(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }

    private record ExamDefinition(int version, int durationSeconds, BigDecimal maxScore, int questionCount) {
    }

    private record Score(BigDecimal rawScore, BigDecimal maxScore, int correctCount) {
    }

    private record AttemptState(String status, Instant expiresAt) {
    }

    private record ReportRef(UUID id, UUID jobId) {
    }

    private record QuestionRow(UUID questionId, String content, String skill, int order, UUID optionId,
            String optionContent, int optionOrder) {
    }

    private record QuestionBuilder(UUID id, String content, String skill, int order,
            List<PlacementDtos.OptionResponse> options) {

        QuestionBuilder(UUID id, String content, String skill, int order) {
            this(id, content, skill, order, new ArrayList<>());
        }

        PlacementDtos.QuestionResponse response() {
            return new PlacementDtos.QuestionResponse(id, content, skill, order, List.copyOf(options));
        }
    }
}
