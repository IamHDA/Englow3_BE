package com.englow3.ai.learningpath;

import java.sql.ResultSet;
import java.sql.SQLException;
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
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
class LearningPathService {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final LearnerProfileRepository profileRepository;
    private final CurrentUser currentUser;
    private final AiPromptService promptService;
    private final AiJobService jobService;
    private final ObjectMapper objectMapper;

    LearningPathService(JdbcTemplate jdbcTemplate, UserRepository userRepository,
            LearnerProfileRepository profileRepository, CurrentUser currentUser, AiPromptService promptService,
            AiJobService jobService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.currentUser = currentUser;
        this.promptService = promptService;
        this.jobService = jobService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    LearningPathDtos.PathResponse generate(LearningPathDtos.GenerateRequest request) {
        User user = requireUser();
        UUID activePathId = activePathId(user.getId());
        if (activePathId != null && !request.regenerate()) {
            return load(activePathId, user.getId());
        }
        if (activePathId != null) {
            jdbcTemplate.update("update learning_paths set status = 'SUPERSEDED' where id = ?", activePathId);
        }
        savePreferences(user.getId(), request.dailyMinutes(), request.items());
        String level = currentLevel(user.getId());
        Set<String> focusDomains = focusDomains(user.getId());
        List<ConceptCandidate> candidates = eligibleConcepts(user.getId(), level).stream()
                .sorted(Comparator.comparing((ConceptCandidate concept) -> !focusDomains.contains(concept.domain()))
                        .thenComparingDouble(ConceptCandidate::mastery).thenComparing(ConceptCandidate::conceptId))
                .limit(request.items()).toList();
        if (candidates.isEmpty()) {
            throw new ConflictException("LEARNING_PATH_NO_ELIGIBLE_CONCEPTS",
                    "No prerequisite-ready concepts are available for this learner");
        }

        UUID pathId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into learning_paths (id, user_id, status) values (?, ?, 'ACTIVE')
                """, pathId, user.getId());
        int position = 1;
        for (ConceptCandidate concept : candidates) {
            String reason = concept.mastery() < 0.5 ? "Build an important weak concept"
                    : "Continue the prerequisite-ready progression";
            jdbcTemplate.update("""
                    insert into learning_path_items
                        (id, learning_path_id, position, concept_id, reason)
                    values (?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), pathId, position++, concept.conceptId(), reason);
        }

        String conceptText = candidates.stream()
                .map(concept -> "%s (%s/%s, mastery %.2f)".formatted(concept.conceptId(), concept.nameEn(),
                        concept.domain(), concept.mastery()))
                .reduce((left, right) -> left + "\n" + right).orElseThrow();
        RenderedPrompt prompt = promptService.render("LEARNING_PATH_EXPLANATION",
                Map.of("level", level, "dailyMinutes", request.dailyMinutes(), "concepts", conceptText));
        ObjectNode payload = objectMapper.createObjectNode().put("pathId", pathId.toString())
                .put("systemPrompt", prompt.systemPrompt()).put("userPrompt", prompt.userPrompt());
        AiJob job = jobService.submitForCurrentUser(AiCapability.LEARNING_PATH, "LEARNING_PATH_EXPLANATION",
                "LEARNING_PATH", pathId, prompt.version(), payload, request.idempotencyKey());
        jdbcTemplate.update("update learning_paths set ai_job_id = ? where id = ?", job.getId(), pathId);
        return load(pathId, user.getId());
    }

    @Transactional(readOnly = true)
    LearningPathDtos.PathResponse current() {
        User user = requireUser();
        UUID pathId = activePathId(user.getId());
        if (pathId == null) {
            throw new NotFoundException("LEARNING_PATH_NOT_FOUND", "No active learning path exists");
        }
        return load(pathId, user.getId());
    }

    @Transactional
    void updatePreferences(LearningPathDtos.PreferencesRequest request) {
        savePreferences(requireUser().getId(), request.dailyMinutes(), request.itemsPerPath());
    }

    @Transactional
    LearningPathDtos.PathItem complete(UUID itemId, LearningPathDtos.CompleteItemRequest request) {
        User user = requireUser();
        ItemState item = jdbcTemplate.query("""
                select i.concept_id, i.status
                from learning_path_items i join learning_paths p on p.id = i.learning_path_id
                where i.id = ? and p.user_id = ? and p.status = 'ACTIVE' for update
                """, rs -> rs.next() ? new ItemState(rs.getString("concept_id"), rs.getString("status")) : null, itemId,
                user.getId());
        if (item == null) {
            throw new NotFoundException("LEARNING_PATH_ITEM_NOT_FOUND", "Learning path item was not found");
        }
        if (!"PENDING".equals(item.status())) {
            throw new ConflictException("LEARNING_PATH_ITEM_ALREADY_HANDLED",
                    "Learning path item is already completed or skipped");
        }
        jdbcTemplate.update("""
                update learning_path_items set status = 'COMPLETED', completed_at = now() where id = ?
                """, itemId);
        updateMastery(user.getId(), item.conceptId(), request.successful());
        jdbcTemplate.update("""
                insert into learning_events
                    (id, user_id, concept_id, learning_path_item_id, event_type, successful, source_type, source_id)
                values (?, ?, ?, ?, 'PATH_ITEM_COMPLETED', ?, 'LEARNING_PATH', ?)
                """, UUID.randomUUID(), user.getId(), item.conceptId(), itemId, request.successful(),
                request.sourceId());
        return findItem(itemId, user.getId());
    }

    @Transactional(readOnly = true)
    LearningPathDtos.PathItem next() {
        User user = requireUser();
        LearningPathDtos.PathItem item = jdbcTemplate.query("""
                select i.id, i.position, i.concept_id, c.name_en, c.name_vi, c.domain,
                       coalesce(m.probability_known, c.p_init) mastery, i.reason, i.status
                from learning_paths p
                join learning_path_items i on i.learning_path_id = p.id
                join concepts c on c.concept_id = i.concept_id
                left join learner_concept_mastery m on m.user_id = p.user_id and m.concept_id = c.concept_id
                where p.user_id = ? and p.status = 'ACTIVE' and i.status = 'PENDING'
                order by i.position limit 1
                """, rs -> rs.next() ? mapItem(rs) : null, user.getId());
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

    private void updateMastery(UUID userId, String conceptId, boolean successful) {
        jdbcTemplate.update("""
                with parameters as (
                    select c.concept_id, c.p_init, c.p_learn, c.p_slip, c.p_guess,
                           coalesce(m.probability_known, c.p_init) prior
                    from concepts c
                    left join learner_concept_mastery m on m.user_id = ? and m.concept_id = c.concept_id
                    where c.concept_id = ?
                ), posterior as (
                    select *, case when ? then
                        (prior * (1 - p_slip)) / nullif(prior * (1 - p_slip) + (1 - prior) * p_guess, 0)
                    else
                        (prior * p_slip) / nullif(prior * p_slip + (1 - prior) * (1 - p_guess), 0)
                    end observed
                    from parameters
                )
                insert into learner_concept_mastery
                    (user_id, concept_id, probability_known, evidence_count, last_practiced_at)
                select ?, concept_id, least(0.99, observed + (1 - observed) * p_learn), 1, now()
                from posterior
                on conflict (user_id, concept_id) do update
                set probability_known = excluded.probability_known,
                    evidence_count = learner_concept_mastery.evidence_count + 1,
                    last_practiced_at = now(), updated_at = now()
                """, userId, conceptId, successful, userId);
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
                       coalesce(m.probability_known, c.p_init) mastery, i.reason, i.status
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
                       coalesce(m.probability_known, c.p_init) mastery, i.reason, i.status
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
                rs.getDouble("mastery"), rs.getString("reason"), rs.getString("status"));
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

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }

    private record ConceptCandidate(String conceptId, String nameEn, String nameVi, String domain, double mastery) {
    }

    private record ItemState(String conceptId, String status) {
    }

    private record PathHeader(UUID id, String status, String explanation, UUID jobId, int dailyMinutes) {
    }
}
