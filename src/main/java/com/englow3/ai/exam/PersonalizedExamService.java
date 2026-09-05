package com.englow3.ai.exam;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.exam.PersonalizedExamSelector.Candidate;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class PersonalizedExamService {

    private static final int POLICY_VERSION = 1;
    private static final int SECONDS_PER_QUESTION = 75;
    private static final String CONCEPT_SEPARATOR = "\u001f";

    private final JdbcTemplate jdbcTemplate;
    private final UserDirectory userDirectory;
    private final PersonalizedExamSelector selector;
    private final ObjectMapper objectMapper;

    PersonalizedExamService(JdbcTemplate jdbcTemplate, UserDirectory userDirectory, PersonalizedExamSelector selector,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDirectory = userDirectory;
        this.selector = selector;
        this.objectMapper = objectMapper;
    }

    @Transactional
    PersonalizedExamDtos.ExamResponse generate(PersonalizedExamDtos.GenerateRequest request) {
        UUID userId = requireUserId();
        if (request.difficultyMax().compareTo(request.difficultyMin()) < 0) {
            throw new BadRequestException("PERSONALIZED_EXAM_DIFFICULTY_INVALID",
                    "Maximum difficulty must not be lower than minimum difficulty");
        }
        String requestHash = requestHash(request);
        Existing existing = existing(userId, request.idempotencyKey());
        if (existing != null) {
            if (!existing.requestHash().equals(requestHash)) {
                throw new ConflictException("PERSONALIZED_EXAM_IDEMPOTENCY_CONFLICT",
                        "The idempotency key was already used for a different exam blueprint");
            }
            return load(existing.examId(), userId);
        }

        UUID seed = UUID.randomUUID();
        List<Candidate> candidates = candidates(userId, request.targetLevel().name());
        List<Candidate> selected = selector.select(candidates, request.skill(), request.questionCount(),
                request.difficultyMin(), request.difficultyMax(), seed);
        if (selected.size() != request.questionCount()) {
            throw new ConflictException("PERSONALIZED_EXAM_POOL_INSUFFICIENT",
                    "The approved question pool cannot satisfy this blueprint without lowering its constraints");
        }

        UUID examId = UUID.randomUUID();
        String title = request.title() == null || request.title().isBlank()
                ? "Personalized %s practice".formatted(request.targetLevel().name())
                : request.title().strip();
        int duration = request.questionCount() * SECONDS_PER_QUESTION;
        jdbcTemplate.update("""
                insert into exams
                    (id, title, description, exam_type, target_level, duration_seconds, max_raw_score,
                     status, version_number, created_by_user_id, published_at)
                values (?, ?, ?, 'PERSONALIZED', ?, ?, ?, 'PUBLISHED', 1, ?, now())
                """, examId, title, "Personalized practice assembled from human-approved content",
                request.targetLevel().name(), duration, BigDecimal.valueOf(request.questionCount()), userId);
        jdbcTemplate.update("""
                insert into personalized_exam_blueprints
                    (exam_id, user_id, target_level, requested_skill, requested_questions,
                     difficulty_min, difficulty_max, selection_policy_version, selection_seed,
                     request_hash, idempotency_key)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, examId, userId, request.targetLevel().name(), request.skill().name(), request.questionCount(),
                request.difficultyMin(), request.difficultyMax(), POLICY_VERSION, seed, requestHash,
                request.idempotencyKey());
        materialize(examId, selected);
        return load(examId, userId);
    }

    @Transactional(readOnly = true)
    List<PersonalizedExamDtos.ExamResponse> history() {
        UUID userId = requireUserId();
        List<UUID> ids = jdbcTemplate.queryForList("""
                select exam_id from personalized_exam_blueprints
                where user_id = ? order by created_at desc limit 100
                """, UUID.class, userId);
        return ids.stream().map(id -> load(id, userId)).toList();
    }

    @Transactional(readOnly = true)
    PersonalizedExamDtos.ExamResponse get(UUID examId) {
        return load(examId, requireUserId());
    }

    private List<Candidate> candidates(UUID userId, String targetLevel) {
        return jdbcTemplate.query("""
                select i.item_id, i.group_id, i.part_number,
                       case when i.part_number <= 4 then 'LISTENING' else 'READING' end skill,
                       i.question_text, i.question_type, i.difficulty_prior, i.explanation_en,
                       i.explanation_vi, i.review_status,
                       coalesce(avg(coalesce(m.probability_known, c.p_init)), 0.5) mastery,
                       exists (
                           select 1 from personalized_exam_sources previous_source
                           join personalized_exam_blueprints previous on previous.exam_id = previous_source.exam_id
                           where previous.user_id = ? and previous_source.source_item_id = i.item_id
                       ) used_before,
                       string_agg(distinct c.concept_id, ? order by c.concept_id) concept_ids
                from exam_items i
                join exam_item_options o on o.item_id = i.item_id
                join exam_item_concepts ic on ic.item_id = i.item_id
                join concepts c on c.concept_id = ic.concept_id
                left join learner_concept_mastery m on m.user_id = ? and m.concept_id = c.concept_id
                where i.review_status in ('human_approved', 'human_verified', 'approved', 'published')
                  and i.question_text is not null
                  and case c.cefr_band_min when 'A1' then 1 when 'A2' then 2 when 'B1' then 3
                        when 'B2' then 4 when 'C1' then 5 else 6 end
                      <= case ? when 'A1' then 1 when 'A2' then 2 when 'B1' then 3
                        when 'B2' then 4 when 'C1' then 5 else 6 end
                  and (i.part_number > 4 or exists (
                        select 1 from audio_assets a where a.group_id = i.group_id and a.audio_url is not null))
                  and (i.part_number <> 1 or exists (
                        select 1 from exam_groups g where g.group_id = i.group_id and g.image_url is not null))
                group by i.item_id, i.group_id, i.part_number, i.question_text, i.question_type,
                         i.difficulty_prior, i.explanation_en, i.explanation_vi, i.review_status
                having count(distinct o.label) between 3 and 4
                   and count(distinct o.label) filter (where o.is_correct) = 1
                limit 2000
                """,
                (rs, row) -> new Candidate(rs.getString("item_id"), rs.getString("group_id"), rs.getInt("part_number"),
                        rs.getString("skill"), rs.getString("question_text"), rs.getString("question_type"),
                        rs.getBigDecimal("difficulty_prior"), rs.getBigDecimal("mastery"), rs.getBoolean("used_before"),
                        rs.getString("explanation_en"), rs.getString("explanation_vi"), rs.getString("review_status"),
                        splitConcepts(rs.getString("concept_ids"))),
                userId, CONCEPT_SEPARATOR, userId, targetLevel);
    }

    private void materialize(UUID examId, List<Candidate> selected) {
        Map<String, UUID> sections = new HashMap<>();
        Map<String, UUID> parts = new HashMap<>();
        Map<String, UUID> sets = new HashMap<>();
        Map<String, Integer> sectionCounts = new HashMap<>();
        for (Candidate candidate : selected) {
            sectionCounts.merge(candidate.skill(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : sectionCounts.entrySet()) {
            UUID sectionId = UUID.randomUUID();
            sections.put(entry.getKey(), sectionId);
            jdbcTemplate.update("""
                    insert into exam_sections
                        (id, exam_id, section_type, order_no, max_raw_score, time_limit_seconds)
                    values (?, ?, ?, ?, ?, ?)
                    """, sectionId, examId, entry.getKey(), entry.getKey().equals("LISTENING") ? 1 : 2,
                    BigDecimal.valueOf(entry.getValue()), entry.getValue() * SECONDS_PER_QUESTION);
        }

        int position = 1;
        for (Candidate candidate : selected) {
            String partKey = candidate.skill() + ':' + candidate.partNumber();
            UUID partId = parts.computeIfAbsent(partKey,
                    ignored -> createPart(sections.get(candidate.skill()), candidate.partNumber()));
            String setKey = partKey + ':' + candidate.groupId();
            UUID setId = sets.computeIfAbsent(setKey, ignored -> createSet(partId, candidate));
            UUID questionId = UUID.randomUUID();
            ObjectNode metadata = objectMapper.createObjectNode().put("sourceItemId", candidate.itemId())
                    .put("sourceGroupId", candidate.groupId()).put("sourceReviewStatus", candidate.reviewStatus());
            jdbcTemplate.update("""
                    insert into questions
                        (id, question_set_id, question_type, content, difficulty_level, skill_type,
                         question_category, order_no, max_raw_score, explanation, metadata)
                    values (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?::jsonb)
                    """, questionId, setId, fit(candidate.questionType(), 20), candidate.question(),
                    difficulty(candidate.difficulty()), candidate.skill(), "AI_PERSONALIZED", position,
                    candidate.explanationEn() + "\n" + candidate.explanationVi(), json(metadata));
            List<Option> options = options(candidate.itemId());
            int optionOrder = 1;
            for (Option option : options) {
                jdbcTemplate.update("""
                        insert into question_options (id, question_id, content, order_no, is_correct)
                        values (?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), questionId, option.text(), optionOrder++, option.correct());
            }
            String sourceHash = sourceHash(candidate, options);
            jdbcTemplate.update("""
                    insert into personalized_exam_sources
                        (exam_id, position, source_item_id, source_group_id, source_review_status, source_content_hash)
                    values (?, ?, ?, ?, ?, ?)
                    """, examId, position++, candidate.itemId(), candidate.groupId(), candidate.reviewStatus(),
                    sourceHash);
        }
    }

    private UUID createPart(UUID sectionId, int partNumber) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into section_parts (id, exam_section_id, order_no, title, instruction)
                values (?, ?, ?, ?, ?)
                """, id, sectionId, partNumber, "Part " + partNumber, "Choose the best answer.");
        return id;
    }

    private UUID createSet(UUID partId, Candidate candidate) {
        UUID id = UUID.randomUUID();
        Context context = context(candidate.groupId());
        jdbcTemplate.update("""
                insert into question_sets
                    (id, section_part_id, title, instruction, order_no, audio_object_key, image_object_key)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, partId, "Personalized item group", context.passage(), 1, context.audio(), context.image());
        return id;
    }

    private Context context(String groupId) {
        return jdbcTemplate.query("""
                select (select string_agg(p.text, E'\n' order by p."order") from passages p
                        where p.group_id = g.group_id) passage,
                       (select a.audio_url from audio_assets a where a.group_id = g.group_id) audio_url,
                       g.image_url
                from exam_groups g where g.group_id = ?
                """,
                rs -> rs.next()
                        ? new Context(rs.getString("passage"), rs.getString("audio_url"), rs.getString("image_url"))
                        : new Context(null, null, null),
                groupId);
    }

    private List<Option> options(String itemId) {
        return jdbcTemplate.query("""
                select text, is_correct from exam_item_options where item_id = ? order by label
                """, (rs, row) -> new Option(rs.getString("text"), rs.getBoolean("is_correct")), itemId);
    }

    private PersonalizedExamDtos.ExamResponse load(UUID examId, UUID userId) {
        ExamHeader header = jdbcTemplate.query("""
                select e.id, e.title, e.target_level, b.requested_skill, b.requested_questions,
                       e.duration_seconds, e.status, e.created_at
                from exams e join personalized_exam_blueprints b on b.exam_id = e.id
                where e.id = ? and b.user_id = ?
                """, rs -> rs.next() ? mapHeader(rs) : null, examId, userId);
        if (header == null) {
            throw new NotFoundException("PERSONALIZED_EXAM_NOT_FOUND", "Personalized exam was not found");
        }
        List<PersonalizedExamDtos.SourceSummary> sources = jdbcTemplate.query("""
                select s.position, s.source_item_id, i.part_number,
                       case when i.part_number <= 4 then 'LISTENING' else 'READING' end skill,
                       i.difficulty_prior,
                       string_agg(ic.concept_id, ? order by ic.concept_id) concept_ids
                from personalized_exam_sources s
                join exam_items i on i.item_id = s.source_item_id
                join exam_item_concepts ic on ic.item_id = i.item_id
                where s.exam_id = ?
                group by s.position, s.source_item_id, i.part_number, i.difficulty_prior
                order by s.position
                """,
                (rs, row) -> new PersonalizedExamDtos.SourceSummary(rs.getInt("position"),
                        rs.getString("source_item_id"), rs.getInt("part_number"), rs.getString("skill"),
                        rs.getBigDecimal("difficulty_prior"), splitConcepts(rs.getString("concept_ids"))),
                CONCEPT_SEPARATOR, examId);
        return new PersonalizedExamDtos.ExamResponse(header.id(), header.title(), header.targetLevel(), header.skill(),
                header.questionCount(), header.duration(), header.status(), sources, header.createdAt());
    }

    private ExamHeader mapHeader(ResultSet rs) throws SQLException {
        return new ExamHeader(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("target_level"),
                rs.getString("requested_skill"), rs.getInt("requested_questions"), rs.getInt("duration_seconds"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant());
    }

    private Existing existing(UUID userId, String key) {
        return jdbcTemplate.query("""
                select exam_id, request_hash from personalized_exam_blueprints
                where user_id = ? and idempotency_key = ?
                """, rs -> rs.next() ? new Existing(rs.getObject("exam_id", UUID.class), rs.getString("request_hash"))
                : null, userId, key);
    }

    private String requestHash(PersonalizedExamDtos.GenerateRequest request) {
        String normalized = "%s|%s|%d|%s|%s|%s".formatted(request.targetLevel(), request.skill(),
                request.questionCount(), request.difficultyMin().stripTrailingZeros().toPlainString(),
                request.difficultyMax().stripTrailingZeros().toPlainString(),
                request.title() == null ? "" : request.title().strip());
        return sha256(normalized);
    }

    private String sourceHash(Candidate candidate, List<Option> options) {
        StringBuilder value = new StringBuilder(candidate.itemId()).append('|').append(candidate.question()).append('|')
                .append(candidate.explanationEn()).append('|').append(candidate.explanationVi());
        options.forEach(option -> value.append('|').append(option.text()).append(':').append(option.correct()));
        return sha256(value.toString());
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private List<String> splitConcepts(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(CONCEPT_SEPARATOR, -1));
    }

    private String difficulty(BigDecimal value) {
        if (value.compareTo(new BigDecimal("0.34")) < 0) {
            return "EASY";
        }
        return value.compareTo(new BigDecimal("0.67")) < 0 ? "MEDIUM" : "HARD";
    }

    private String fit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }

    private String json(ObjectNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize personalized exam metadata", ex);
        }
    }

    private UUID requireUserId() {
        return userDirectory.requireCurrentUserId();
    }

    private record Existing(UUID examId, String requestHash) {
    }

    private record ExamHeader(UUID id, String title, String targetLevel, String skill, int questionCount, int duration,
            String status, Instant createdAt) {
    }

    private record Option(String text, boolean correct) {
    }

    private record Context(String passage, String audio, String image) {
    }
}
