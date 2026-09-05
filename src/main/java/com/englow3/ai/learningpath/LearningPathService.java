package com.englow3.ai.learningpath;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.foundation.AiJob;
import com.englow3.ai.foundation.AiJobService;
import com.englow3.ai.foundation.AiPromptService;
import com.englow3.ai.foundation.RenderedPrompt;
import com.englow3.ai.learningpath.LearningContentResolver.ContentRef;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.service.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class LearningPathService {

    private final JdbcTemplate jdbcTemplate;
    private final UserDirectory userDirectory;
    private final LearnerProfileRepository profileRepository;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final ObjectMapper objectMapper;
    private final LearningContentResolver contentResolver;
    private final BktMasteryCalculator masteryCalculator;

    LearningPathService(JdbcTemplate jdbcTemplate, UserDirectory userDirectory,
            LearnerProfileRepository profileRepository, AiPromptService promptService, AiJobService jobService,
            ObjectMapper objectMapper, LearningContentResolver contentResolver,
            BktMasteryCalculator masteryCalculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDirectory = userDirectory;
        this.profileRepository = profileRepository;
        this.promptService = promptService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
        this.contentResolver = contentResolver;
        this.masteryCalculator = masteryCalculator;
    }

    @Transactional
    LearningPathDtos.PathResponse generate(LearningPathDtos.GenerateRequest request) {
        UUID userId = requireUserId();
        UUID activePathId = activePathId(userId);
        if (activePathId != null && !request.regenerate()) {
            return load(activePathId, userId);
        }
        if (activePathId != null) {
            jdbcTemplate.update("update learning_paths set status = 'SUPERSEDED' where id = ?", activePathId);
        }
        savePreferences(userId, request.dailyMinutes(), request.items());
        String level = currentLevel(userId);
        Set<String> focusDomains = focusDomains(userId);
        List<ConceptCandidate> rankedConcepts = eligibleConcepts(userId, level).stream()
                .sorted(Comparator.comparing((ConceptCandidate concept) -> !focusDomains.contains(concept.domain()))
                        .thenComparingDouble(ConceptCandidate::mastery).thenComparing(ConceptCandidate::conceptId))
                .toList();
        List<PathCandidate> candidates = new ArrayList<>();
        for (ConceptCandidate concept : rankedConcepts) {
            ContentRef content = contentResolver.resolve(userId, concept.conceptId(), concept.mastery());
            if (content != null) {
                candidates.add(new PathCandidate(concept, content));
            }
            if (candidates.size() == request.items()) {
                break;
            }
        }
        if (candidates.isEmpty()) {
            throw new ConflictException("LEARNING_PATH_NO_ELIGIBLE_CONCEPTS",
                    "No prerequisite-ready concepts are available for this learner");
        }

        UUID pathId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into learning_paths (id, user_id, status) values (?, ?, 'ACTIVE')
                """, pathId, userId);
        int position = 1;
        for (PathCandidate candidate : candidates) {
            ConceptCandidate concept = candidate.concept();
            String reason = concept.mastery() < 0.5 ? "Build an important weak concept"
                    : "Continue the prerequisite-ready progression";
            jdbcTemplate.update("""
                    insert into learning_path_items
                        (id, learning_path_id, position, concept_id, content_type, content_id,
                         content_difficulty, reason)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), pathId, position++, concept.conceptId(), candidate.content().type(),
                    candidate.content().id(), candidate.content().difficulty(), reason);
        }

        if (!jobService.isEnabled(AiCapability.LEARNING_PATH)) {
            return load(pathId, userId);
        }

        String conceptText = candidates.stream().map(PathCandidate::concept)
                .map(concept -> "%s (%s/%s, mastery %.2f)".formatted(concept.conceptId(), concept.nameEn(),
                        concept.domain(), concept.mastery()))
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        RenderedPrompt prompt = promptService.render("LEARNING_PATH_EXPLANATION",
                Map.of("level", level, "dailyMinutes", request.dailyMinutes(), "concepts", conceptText));
        ObjectNode payload = objectMapper.createObjectNode().put("pathId", pathId.toString())
                .put("systemPrompt", prompt.systemPrompt()).put("userPrompt", prompt.userPrompt());
        AiJob job = jobService.submitForCurrentUser(AiCapability.LEARNING_PATH, "LEARNING_PATH_EXPLANATION",
                "LEARNING_PATH", pathId, prompt.version(), payload, "LEARNING_PATH:" + request.idempotencyKey());
        jdbcTemplate.update("update learning_paths set ai_job_id = ? where id = ?", job.getId(), pathId);
        return load(pathId, userId);
    }

    @Transactional(readOnly = true)
    LearningPathDtos.PathResponse current() {
        UUID userId = requireUserId();
        UUID pathId = activePathId(userId);
        if (pathId == null) {
            throw new NotFoundException("LEARNING_PATH_NOT_FOUND", "No active learning path exists");
        }
        return load(pathId, userId);
    }

    @Transactional
    void updatePreferences(LearningPathDtos.PreferencesRequest request) {
        savePreferences(requireUserId(), request.dailyMinutes(), request.itemsPerPath());
    }

    @Transactional
    LearningPathDtos.PathItem complete(UUID itemId, LearningPathDtos.CompleteItemRequest request) {
        UUID userId = requireUserId();
        if (isIdempotentReplay(userId, itemId, request.idempotencyKey(), "PATH_ITEM_COMPLETED", request.successful())) {
            return findItem(itemId, userId);
        }
        ItemState item = lockItem(itemId, userId);
        if (!List.of("PENDING", "POSTPONED").contains(item.status())) {
            throw new ConflictException("LEARNING_PATH_ITEM_ALREADY_HANDLED",
                    "Learning path item is already completed or skipped");
        }
        jdbcTemplate.update("""
                update learning_path_items set status = 'COMPLETED', completed_at = now() where id = ?
                """, itemId);
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into learning_events
                    (id, user_id, concept_id, learning_path_item_id, event_type, successful,
                     source_type, source_id, idempotency_key, duration_seconds, difficulty)
                values (?, ?, ?, ?, 'PATH_ITEM_COMPLETED', ?, ?, ?, ?, ?, ?)
                """, eventId, userId, item.conceptId(), itemId, request.successful(), sourceType(item),
                sourceId(item, itemId), request.idempotencyKey(), request.durationSeconds(), item.difficulty());
        updateMastery(eventId, userId, item.conceptId(), request.successful());
        completePathWhenDone(item.pathId());
        return findItem(itemId, userId);
    }

    @Transactional
    LearningPathDtos.PathItem skip(UUID itemId, LearningPathDtos.SkipItemRequest request) {
        UUID userId = requireUserId();
        if (isIdempotentReplay(userId, itemId, request.idempotencyKey(), "PATH_ITEM_SKIPPED", null)) {
            return findItem(itemId, userId);
        }
        ItemState item = lockItem(itemId, userId);
        if (!List.of("PENDING", "POSTPONED").contains(item.status())) {
            throw new ConflictException("LEARNING_PATH_ITEM_ALREADY_HANDLED", "Learning path item is already handled");
        }
        jdbcTemplate.update("""
                update learning_path_items
                set status = 'SKIPPED', skip_reason = ?, postponed_until = null where id = ?
                """, request.reason().strip(), itemId);
        recordEvent(UUID.randomUUID(), userId, item, itemId, "PATH_ITEM_SKIPPED", null, request.idempotencyKey());
        completePathWhenDone(item.pathId());
        return findItem(itemId, userId);
    }

    @Transactional
    LearningPathDtos.PathItem postpone(UUID itemId, LearningPathDtos.PostponeItemRequest request) {
        UUID userId = requireUserId();
        if (!request.until().isAfter(Instant.now())
                || request.until().isAfter(Instant.now().plusSeconds(30L * 86400))) {
            throw new BadRequestException("LEARNING_PATH_POSTPONE_INVALID",
                    "Postponement must be in the future and no more than 30 days");
        }
        if (isIdempotentReplay(userId, itemId, request.idempotencyKey(), "PATH_ITEM_POSTPONED", null)) {
            return findItem(itemId, userId);
        }
        ItemState item = lockItem(itemId, userId);
        if (!List.of("PENDING", "POSTPONED").contains(item.status())) {
            throw new ConflictException("LEARNING_PATH_ITEM_ALREADY_HANDLED", "Learning path item is already handled");
        }
        jdbcTemplate.update("""
                update learning_path_items set status = 'POSTPONED', postponed_until = ? where id = ?
                """, Timestamp.from(request.until()), itemId);
        recordEvent(UUID.randomUUID(), userId, item, itemId, "PATH_ITEM_POSTPONED", null, request.idempotencyKey());
        return findItem(itemId, userId);
    }

    @Transactional
    LearningPathDtos.PathItem replace(UUID itemId, LearningPathDtos.ReplaceItemRequest request) {
        UUID userId = requireUserId();
        if (isIdempotentReplay(userId, itemId, request.idempotencyKey(), "PATH_ITEM_REPLACED", null)) {
            return findItem(itemId, userId);
        }
        ItemState item = lockItem(itemId, userId);
        if (!List.of("PENDING", "POSTPONED").contains(item.status())) {
            throw new ConflictException("LEARNING_PATH_ITEM_ALREADY_HANDLED", "Learning path item is already handled");
        }
        ContentRef replacement = contentResolver.resolve(userId, item.conceptId(), item.mastery(), item.contentType(),
                item.contentId());
        if (replacement == null) {
            throw new ConflictException("LEARNING_PATH_REPLACEMENT_NOT_FOUND",
                    "No alternative approved content is available for this concept");
        }
        jdbcTemplate.update("""
                insert into learning_path_item_replacements
                    (id, learning_path_item_id, old_content_type, old_content_id,
                     new_content_type, new_content_id, reason)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), itemId, sourceType(item), sourceId(item, itemId), replacement.type(),
                replacement.id(), request.reason().strip());
        jdbcTemplate.update(
                """
                        update learning_path_items
                        set content_type = ?, content_id = ?, content_difficulty = ?, status = 'PENDING', postponed_until = null,
                            reason = ? where id = ?
                        """,
                replacement.type(), replacement.id(), replacement.difficulty(),
                "Replacement: " + request.reason().strip(), itemId);
        recordEvent(UUID.randomUUID(), userId, item, itemId, "PATH_ITEM_REPLACED", null, request.idempotencyKey());
        return findItem(itemId, userId);
    }

    @Transactional
    LearningPathDtos.PathItem next() {
        UUID userId = requireUserId();
        jdbcTemplate.update("""
                update learning_path_items i set status = 'PENDING', postponed_until = null
                from learning_paths p
                where i.learning_path_id = p.id and p.user_id = ? and p.status = 'ACTIVE'
                  and i.status = 'POSTPONED' and i.postponed_until <= now()
                """, userId);
        LearningPathDtos.PathItem item = jdbcTemplate.query("""
                select i.id, i.position, i.concept_id, c.name_en, c.name_vi, c.domain,
                       coalesce(m.probability_known, c.p_init) mastery, i.content_type, i.content_id,
                       i.reason, i.status, i.postponed_until
                from learning_paths p
                join learning_path_items i on i.learning_path_id = p.id
                join concepts c on c.concept_id = i.concept_id
                left join learner_concept_mastery m on m.user_id = p.user_id and m.concept_id = c.concept_id
                where p.user_id = ? and p.status = 'ACTIVE' and i.status = 'PENDING'
                order by i.position limit 1
                """, rs -> rs.next() ? mapItem(rs) : null, userId);
        if (item == null) {
            throw new NotFoundException("LEARNING_RECOMMENDATION_NOT_FOUND", "No pending recommendation exists");
        }
        return item;
    }

    private List<ConceptCandidate> eligibleConcepts(UUID userId, String level) {
        return jdbcTemplate.query("""
                select c.concept_id, c.name_en, c.name_vi, c.domain,
                       coalesce(m.probability_known, c.p_init) mastery
                from concepts c
                left join learner_concept_mastery m on m.user_id = ? and m.concept_id = c.concept_id
                where case c.cefr_band_min when 'A1' then 1 when 'A2' then 2 when 'B1' then 3
                          when 'B2' then 4 when 'C1' then 5 else 6 end
                      <= case ? when 'A1' then 1 when 'A2' then 2 when 'B1' then 3
                          when 'B2' then 4 when 'C1' then 5 else 6 end
                  and coalesce(m.probability_known, c.p_init) < 0.95
                  and not exists (
                      select 1 from concept_prerequisites cp
                      join concepts prerequisite on prerequisite.concept_id = cp.prerequisite_id
                      left join learner_concept_mastery pm
                        on pm.user_id = ? and pm.concept_id = cp.prerequisite_id
                      where cp.concept_id = c.concept_id
                        and coalesce(pm.probability_known, prerequisite.p_init) < 0.70
                  )
                limit 100
                """,
                (rs, row) -> new ConceptCandidate(rs.getString("concept_id"), rs.getString("name_en"),
                        rs.getString("name_vi"), rs.getString("domain"), rs.getDouble("mastery")),
                userId, level, userId);
    }

    private void updateMastery(UUID eventId, UUID userId, String conceptId, boolean successful) {
        jdbcTemplate.queryForObject("select pg_advisory_xact_lock(hashtextextended(?, 0))::text", String.class,
                userId + ":" + conceptId);
        MasteryParameters parameters = jdbcTemplate.query("""
                select coalesce(m.probability_known, c.p_init) prior,
                       c.p_learn, c.p_slip, c.p_guess
                from concepts c
                left join learner_concept_mastery m on m.user_id = ? and m.concept_id = c.concept_id
                where c.concept_id = ?
                """,
                rs -> rs.next()
                        ? new MasteryParameters(rs.getDouble("prior"), rs.getDouble("p_learn"), rs.getDouble("p_slip"),
                                rs.getDouble("p_guess"))
                        : null,
                userId, conceptId);
        if (parameters == null) {
            throw new NotFoundException("LEARNING_CONCEPT_NOT_FOUND", "Learning concept was not found");
        }
        BktMasteryCalculator.Update update = masteryCalculator.calculate(parameters.prior(), parameters.learn(),
                parameters.slip(), parameters.guess(), successful);
        jdbcTemplate.update("""
                insert into learner_concept_mastery
                    (user_id, concept_id, probability_known, evidence_count, last_practiced_at)
                values (?, ?, ?, 1, now())
                on conflict (user_id, concept_id) do update
                set probability_known = excluded.probability_known,
                    evidence_count = learner_concept_mastery.evidence_count + 1,
                    last_practiced_at = now(), updated_at = now()
                """, userId, conceptId, update.posterior());
        jdbcTemplate.update("""
                insert into learner_mastery_events
                    (event_id, user_id, concept_id, successful, prior_probability, observed_probability,
                     posterior_probability, p_learn, p_slip, p_guess, algorithm_version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, userId, conceptId, successful, update.prior(), update.observed(), update.posterior(),
                parameters.learn(), parameters.slip(), parameters.guess(), BktMasteryCalculator.ALGORITHM_VERSION);
    }

    private LearningPathDtos.PathResponse load(UUID pathId, UUID userId) {
        PathHeader header = jdbcTemplate.query("""
                select p.id, p.status, p.explanation, p.ai_job_id, coalesce(pref.daily_minutes, 20) daily_minutes
                from learning_paths p
                left join learning_path_preferences pref on pref.user_id = p.user_id
                where p.id = ? and p.user_id = ?
                """,
                rs -> rs.next() ? new PathHeader(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getString("explanation"), rs.getObject("ai_job_id", UUID.class), rs.getInt("daily_minutes"))
                        : null,
                pathId, userId);
        if (header == null) {
            throw new NotFoundException("LEARNING_PATH_NOT_FOUND", "Learning path was not found");
        }
        List<LearningPathDtos.PathItem> items = jdbcTemplate.query("""
                select i.id, i.position, i.concept_id, c.name_en, c.name_vi, c.domain,
                       coalesce(m.probability_known, c.p_init) mastery, i.content_type, i.content_id,
                       i.reason, i.status, i.postponed_until
                from learning_path_items i
                join learning_paths p on p.id = i.learning_path_id
                join concepts c on c.concept_id = i.concept_id
                left join learner_concept_mastery m on m.user_id = p.user_id and m.concept_id = c.concept_id
                where i.learning_path_id = ? order by i.position
                """, (rs, row) -> mapItem(rs), pathId);
        return new LearningPathDtos.PathResponse(header.id(), header.status(), header.explanation(), header.jobId(),
                header.dailyMinutes(), items);
    }

    private LearningPathDtos.PathItem findItem(UUID itemId, UUID userId) {
        return jdbcTemplate.query("""
                select i.id, i.position, i.concept_id, c.name_en, c.name_vi, c.domain,
                       coalesce(m.probability_known, c.p_init) mastery, i.content_type, i.content_id,
                       i.reason, i.status, i.postponed_until
                from learning_path_items i
                join learning_paths p on p.id = i.learning_path_id
                join concepts c on c.concept_id = i.concept_id
                left join learner_concept_mastery m on m.user_id = p.user_id and m.concept_id = c.concept_id
                where i.id = ? and p.user_id = ?
                """, rs -> rs.next() ? mapItem(rs) : null, itemId, userId);
    }

    private LearningPathDtos.PathItem mapItem(ResultSet rs) throws SQLException {
        return new LearningPathDtos.PathItem(rs.getObject("id", UUID.class), rs.getInt("position"),
                rs.getString("concept_id"), rs.getString("name_en"), rs.getString("name_vi"), rs.getString("domain"),
                rs.getDouble("mastery"), rs.getString("content_type"), rs.getString("content_id"),
                rs.getString("reason"), rs.getString("status"), instant(rs.getTimestamp("postponed_until")));
    }

    private ItemState lockItem(UUID itemId, UUID userId) {
        ItemState item = jdbcTemplate.query("""
                select i.learning_path_id, i.concept_id, i.status, i.content_type, i.content_id,
                       i.content_difficulty, coalesce(m.probability_known, c.p_init) mastery
                from learning_path_items i
                join learning_paths p on p.id = i.learning_path_id
                join concepts c on c.concept_id = i.concept_id
                left join learner_concept_mastery m on m.user_id = p.user_id and m.concept_id = i.concept_id
                where i.id = ? and p.user_id = ? and p.status = 'ACTIVE' for update of i
                """,
                rs -> rs.next()
                        ? new ItemState(rs.getObject("learning_path_id", UUID.class), rs.getString("concept_id"),
                                rs.getString("status"), rs.getString("content_type"), rs.getString("content_id"),
                                rs.getBigDecimal("content_difficulty"), rs.getDouble("mastery"))
                        : null,
                itemId, userId);
        if (item == null) {
            throw new NotFoundException("LEARNING_PATH_ITEM_NOT_FOUND", "Learning path item was not found");
        }
        return item;
    }

    private boolean isIdempotentReplay(UUID userId, UUID itemId, String idempotencyKey, String eventType,
            Boolean successful) {
        ReplayEvent existing = jdbcTemplate.query("""
                select learning_path_item_id, event_type, successful from learning_events
                where user_id = ? and idempotency_key = ?
                """,
                rs -> rs.next()
                        ? new ReplayEvent(rs.getObject("learning_path_item_id", UUID.class), rs.getString("event_type"),
                                rs.getObject("successful", Boolean.class))
                        : null,
                userId, idempotencyKey);
        if (existing == null) {
            return false;
        }
        if (!existing.itemId().equals(itemId) || !existing.eventType().equals(eventType)
                || !java.util.Objects.equals(existing.successful(), successful)) {
            throw new ConflictException("LEARNING_EVENT_IDEMPOTENCY_CONFLICT",
                    "The idempotency key was already used for a different learning event");
        }
        return true;
    }

    private void recordEvent(UUID eventId, UUID userId, ItemState item, UUID itemId, String eventType,
            Boolean successful, String idempotencyKey) {
        jdbcTemplate.update("""
                insert into learning_events
                    (id, user_id, concept_id, learning_path_item_id, event_type, successful,
                     source_type, source_id, idempotency_key, difficulty)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, userId, item.conceptId(), itemId, eventType, successful, sourceType(item),
                sourceId(item, itemId), idempotencyKey, item.difficulty());
    }

    private String sourceType(ItemState item) {
        return item.contentType() == null ? "LEARNING_PATH" : item.contentType();
    }

    private String sourceId(ItemState item, UUID itemId) {
        return item.contentId() == null ? itemId.toString() : item.contentId();
    }

    private void completePathWhenDone(UUID pathId) {
        jdbcTemplate.update("""
                update learning_paths set status = 'COMPLETED'
                where id = ? and status = 'ACTIVE'
                  and not exists (select 1 from learning_path_items
                                  where learning_path_id = ? and status in ('PENDING', 'POSTPONED'))
                """, pathId, pathId);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private UUID activePathId(UUID userId) {
        return jdbcTemplate.query("select id from learning_paths where user_id = ? and status = 'ACTIVE'",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null, userId);
    }

    private void savePreferences(UUID userId, int dailyMinutes, int items) {
        jdbcTemplate.update("""
                insert into learning_path_preferences (user_id, daily_minutes, items_per_path)
                values (?, ?, ?)
                on conflict (user_id) do update
                set daily_minutes = excluded.daily_minutes, items_per_path = excluded.items_per_path
                """, userId, dailyMinutes, items);
    }

    private String currentLevel(UUID userId) {
        return profileRepository.findByUserId(userId).map(profile -> profile.getCurrentLevel()).map(Enum::name)
                .orElse("A1");
    }

    private Set<String> focusDomains(UUID userId) {
        List<String> skills = jdbcTemplate.queryForList("select skill from user_target_skills where user_id = ?",
                String.class, userId);
        Set<String> domains = new HashSet<>();
        skills.forEach(skill -> domains.add(skill.toLowerCase()));
        return domains;
    }

    private UUID requireUserId() {
        return userDirectory.requireCurrentUserId();
    }

    private record ConceptCandidate(String conceptId, String nameEn, String nameVi, String domain, double mastery) {
    }

    private record PathCandidate(ConceptCandidate concept, ContentRef content) {
    }

    private record ItemState(UUID pathId, String conceptId, String status, String contentType, String contentId,
            BigDecimal difficulty, double mastery) {
    }

    private record MasteryParameters(double prior, double learn, double slip, double guess) {
    }

    private record ReplayEvent(UUID itemId, String eventType, Boolean successful) {
    }

    private record PathHeader(UUID id, String status, String explanation, UUID jobId, int dailyMinutes) {
    }
}
