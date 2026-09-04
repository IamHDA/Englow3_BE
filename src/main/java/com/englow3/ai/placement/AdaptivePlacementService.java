package com.englow3.ai.placement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

@Service
class AdaptivePlacementService {

    private static final double COMPLETION_STANDARD_ERROR = 0.35;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final PlacementService fixedPlacementService;
    private final IrtCalculator calculator;
    private final AdaptivePlacementSelector selector;

    AdaptivePlacementService(JdbcTemplate jdbcTemplate, UserRepository userRepository, CurrentUser currentUser,
            PlacementService fixedPlacementService, IrtCalculator calculator) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.fixedPlacementService = fixedPlacementService;
        this.calculator = calculator;
        this.selector = new AdaptivePlacementSelector(calculator);
    }

    @Transactional
    AdaptivePlacementDtos.AttemptResponse start(AdaptivePlacementDtos.StartRequest request) {
        if (request.maxItems() < request.minItems()) {
            throw new BadRequestException("ADAPTIVE_PLACEMENT_ITEM_LIMITS",
                    "maxItems must be greater than or equal to minItems");
        }
        User user = requireUser();
        Integer active = jdbcTemplate.queryForObject("""
                select count(*) from adaptive_placement_attempts
                where user_id = ? and status = 'IN_PROGRESS'
                """, Integer.class, user.getId());
        if (active != null && active > 0) {
            throw new ConflictException("ADAPTIVE_PLACEMENT_ACTIVE",
                    "An adaptive placement attempt is already in progress");
        }

        ActiveCalibration calibration = activeCalibration();
        List<AdaptivePlacementSelector.Candidate> pool = calibration == null ? List.of()
                : candidates(calibration.version(), calibration.minimumResponses(), null);
        UUID attemptId = UUID.randomUUID();
        if (pool.size() < request.minItems()) {
            PlacementDtos.StartAttemptResponse fallback = fixedPlacementService.start(request.fallbackExamId());
            jdbcTemplate.update("""
                    insert into adaptive_placement_attempts
                        (id, user_id, status, fallback_exam_attempt_id, min_items, max_items)
                    values (?, ?, 'FALLBACK', ?, ?, ?)
                    """, attemptId, user.getId(), fallback.attemptId(), request.minItems(), request.maxItems());
            return new AdaptivePlacementDtos.AttemptResponse(attemptId, "FIXED", "FALLBACK", null, fallback.attemptId(),
                    0, null, null, null, null);
        }

        AdaptivePlacementSelector.Candidate first = selector.select(pool, 0);
        jdbcTemplate.update("""
                insert into adaptive_placement_attempts
                    (id, user_id, status, calibration_version, current_theta, selected_item_id,
                     min_items, max_items)
                values (?, ?, 'IN_PROGRESS', ?, 0, ?, ?, ?)
                """, attemptId, user.getId(), calibration.version(), first.itemId(), request.minItems(),
                request.maxItems());
        return response(loadOwned(attemptId, user.getId(), false));
    }

    @Transactional(readOnly = true)
    AdaptivePlacementDtos.AttemptResponse get(UUID attemptId) {
        return response(loadOwned(attemptId, requireUser().getId(), false));
    }

    @Transactional
    AdaptivePlacementDtos.AttemptResponse answer(UUID attemptId, AdaptivePlacementDtos.AnswerRequest request) {
        UUID userId = requireUser().getId();
        Attempt attempt = loadOwned(attemptId, userId, true);
        if (!"IN_PROGRESS".equals(attempt.status())) {
            throw new ConflictException("ADAPTIVE_PLACEMENT_NOT_ACTIVE",
                    "Only an in-progress adaptive attempt accepts answers");
        }
        String label = request.selectedLabel().strip().toUpperCase(java.util.Locale.ROOT);
        ExistingAnswer existing = jdbcTemplate.query("""
                select item_id, selected_label from adaptive_placement_responses
                where attempt_id = ? and idempotency_key = ?
                """,
                rs -> rs.next() ? new ExistingAnswer(rs.getString("item_id"), rs.getString("selected_label")) : null,
                attemptId, request.idempotencyKey());
        if (existing != null) {
            if (!existing.label().equals(label)) {
                throw new ConflictException("ADAPTIVE_PLACEMENT_IDEMPOTENCY_CONFLICT",
                        "The idempotency key was already used for a different answer");
            }
            return response(attempt);
        }
        if (attempt.selectedItemId() == null) {
            throw new ConflictException("ADAPTIVE_PLACEMENT_ITEM_MISSING", "No item is selected for this attempt");
        }
        AnswerKey answer = jdbcTemplate.query("""
                select is_correct from exam_item_options where item_id = ? and label = ?
                """, rs -> rs.next() ? new AnswerKey(rs.getBoolean("is_correct")) : null, attempt.selectedItemId(),
                label);
        if (answer == null) {
            throw new BadRequestException("ADAPTIVE_PLACEMENT_OPTION_INVALID",
                    "The selected label does not belong to the current item");
        }
        IrtCalculator.Parameters parameters = parameters(attempt.calibrationVersion(), attempt.selectedItemId());
        double information = calculator.information(attempt.theta(), parameters);
        double nextTheta = calculator.update(attempt.theta(), answer.correct(), parameters);
        int responseCount = attempt.responseCount() + 1;
        jdbcTemplate.update("""
                insert into adaptive_placement_responses
                    (attempt_id, ordinal, item_id, selected_label, correct, theta_before, theta_after,
                     item_information, discrimination, difficulty, guessing, calibration_version,
                     idempotency_key)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, attemptId, responseCount, attempt.selectedItemId(), label, answer.correct(), attempt.theta(),
                nextTheta, information, parameters.discrimination(), parameters.difficulty(), parameters.guessing(),
                attempt.calibrationVersion(), request.idempotencyKey());
        Double totalInformation = jdbcTemplate.queryForObject("""
                select sum(item_information) from adaptive_placement_responses where attempt_id = ?
                """, Double.class, attemptId);
        double standardError = 1.0 / Math.sqrt(Math.max(1.0e-9, totalInformation == null ? 0 : totalInformation));
        boolean complete = responseCount >= attempt.maxItems()
                || responseCount >= attempt.minItems() && standardError <= COMPLETION_STANDARD_ERROR;
        if (complete) {
            String level = level(nextTheta);
            jdbcTemplate.update("""
                    update adaptive_placement_attempts
                    set status = 'COMPLETED', current_theta = ?, standard_error = ?, response_count = ?,
                        selected_item_id = null, assessed_level = ?, completed_at = now()
                    where id = ?
                    """, nextTheta, standardError, responseCount, level, attemptId);
            jdbcTemplate.update("""
                    insert into learner_profiles (id, user_id, current_level)
                    values (?, ?, ?)
                    on conflict (user_id) do update set current_level = excluded.current_level
                    """, UUID.randomUUID(), userId, level);
        } else {
            List<AdaptivePlacementSelector.Candidate> remaining = candidates(attempt.calibrationVersion(), 0,
                    attemptId);
            if (remaining.isEmpty()) {
                String level = level(nextTheta);
                jdbcTemplate.update("""
                        update adaptive_placement_attempts
                        set status = 'COMPLETED', current_theta = ?, standard_error = ?, response_count = ?,
                            selected_item_id = null, assessed_level = ?, completed_at = now()
                        where id = ?
                        """, nextTheta, standardError, responseCount, level, attemptId);
                jdbcTemplate.update("""
                        insert into learner_profiles (id, user_id, current_level)
                        values (?, ?, ?) on conflict (user_id) do update set current_level = excluded.current_level
                        """, UUID.randomUUID(), userId, level);
            } else {
                String selected = selector.select(remaining, nextTheta).itemId();
                jdbcTemplate.update("""
                        update adaptive_placement_attempts
                        set current_theta = ?, standard_error = ?, response_count = ?, selected_item_id = ?
                        where id = ?
                        """, nextTheta, standardError, responseCount, selected, attemptId);
            }
        }
        return response(loadOwned(attemptId, userId, false));
    }

    private ActiveCalibration activeCalibration() {
        return jdbcTemplate.query("""
                select version, minimum_responses from irt_calibration_versions where status = 'ACTIVE'
                """,
                rs -> rs.next() ? new ActiveCalibration(rs.getInt("version"), rs.getInt("minimum_responses")) : null);
    }

    private List<AdaptivePlacementSelector.Candidate> candidates(int version, int minimumResponses,
            UUID excludingAttempt) {
        return jdbcTemplate.query("""
                select p.item_id, p.discrimination, p.difficulty, p.guessing
                from irt_item_parameters p
                join exam_items i on i.item_id = p.item_id
                where p.calibration_version = ? and p.response_count >= ?
                  and i.review_status = 'human_approved' and i.question_text is not null
                  and (select count(*) from exam_item_options o where o.item_id = i.item_id) between 3 and 4
                  and (select count(*) from exam_item_options o where o.item_id = i.item_id and o.is_correct) = 1
                  and (?::uuid is null or not exists (
                      select 1 from adaptive_placement_responses r
                      where r.attempt_id = ? and r.item_id = i.item_id))
                """,
                (rs, row) -> new AdaptivePlacementSelector.Candidate(rs.getString("item_id"),
                        new IrtCalculator.Parameters(rs.getDouble("discrimination"), rs.getDouble("difficulty"),
                                rs.getDouble("guessing"))),
                version, minimumResponses, excludingAttempt, excludingAttempt);
    }

    private IrtCalculator.Parameters parameters(int version, String itemId) {
        IrtCalculator.Parameters result = jdbcTemplate.query("""
                select discrimination, difficulty, guessing from irt_item_parameters
                where calibration_version = ? and item_id = ?
                """,
                rs -> rs.next()
                        ? new IrtCalculator.Parameters(rs.getDouble("discrimination"), rs.getDouble("difficulty"),
                                rs.getDouble("guessing"))
                        : null,
                version, itemId);
        if (result == null) {
            throw new ConflictException("IRT_PARAMETERS_MISSING", "The immutable item parameters were not found");
        }
        return result;
    }

    private Attempt loadOwned(UUID attemptId, UUID userId, boolean lock) {
        Attempt attempt = jdbcTemplate.query("""
                select id, status, calibration_version, fallback_exam_attempt_id, current_theta,
                       standard_error, selected_item_id, response_count, min_items, max_items, assessed_level
                from adaptive_placement_attempts where id = ? and user_id = ?
                """ + (lock ? " for update" : ""), rs -> rs.next() ? mapAttempt(rs) : null, attemptId, userId);
        if (attempt == null) {
            throw new NotFoundException("ADAPTIVE_PLACEMENT_NOT_FOUND", "The adaptive placement attempt was not found");
        }
        return attempt;
    }

    private Attempt mapAttempt(ResultSet rs) throws SQLException {
        return new Attempt(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getObject("calibration_version", Integer.class),
                rs.getObject("fallback_exam_attempt_id", UUID.class), rs.getDouble("current_theta"),
                rs.getObject("standard_error", Double.class), rs.getString("selected_item_id"),
                rs.getInt("response_count"), rs.getInt("min_items"), rs.getInt("max_items"),
                rs.getString("assessed_level"));
    }

    private AdaptivePlacementDtos.AttemptResponse response(Attempt attempt) {
        AdaptivePlacementDtos.Item next = attempt.selectedItemId() == null ? null : item(attempt.selectedItemId());
        return new AdaptivePlacementDtos.AttemptResponse(attempt.id(),
                "FALLBACK".equals(attempt.status()) ? "FIXED" : "ADAPTIVE", attempt.status(),
                attempt.calibrationVersion(), attempt.fallbackAttemptId(), attempt.responseCount(),
                "FALLBACK".equals(attempt.status()) ? null : attempt.theta(), attempt.standardError(),
                attempt.assessedLevel(), next);
    }

    private AdaptivePlacementDtos.Item item(String itemId) {
        List<ItemRow> rows = jdbcTemplate.query("""
                select i.item_id, i.question_text, i.part_number, o.label, o.text
                from exam_items i join exam_item_options o on o.item_id = i.item_id
                where i.item_id = ? order by o.label
                """, (rs, row) -> new ItemRow(rs.getString("item_id"), rs.getString("question_text"),
                rs.getInt("part_number"), rs.getString("label"), rs.getString("text")), itemId);
        if (rows.isEmpty()) {
            throw new ConflictException("ADAPTIVE_PLACEMENT_ITEM_MISSING", "The selected item is unavailable");
        }
        List<AdaptivePlacementDtos.Option> options = new ArrayList<>();
        rows.forEach(row -> options.add(new AdaptivePlacementDtos.Option(row.label(), row.text())));
        ItemRow first = rows.getFirst();
        return new AdaptivePlacementDtos.Item(first.itemId(), first.question(), first.partNumber(),
                List.copyOf(options));
    }

    private String level(double theta) {
        if (theta < -1.5) {
            return "A1";
        }
        if (theta < -0.5) {
            return "A2";
        }
        if (theta < 0.5) {
            return "B1";
        }
        if (theta < 1.5) {
            return "B2";
        }
        return "C1";
    }

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }

    private record ActiveCalibration(int version, int minimumResponses) {
    }

    private record Attempt(UUID id, String status, Integer calibrationVersion, UUID fallbackAttemptId, double theta,
            Double standardError, String selectedItemId, int responseCount, int minItems, int maxItems,
            String assessedLevel) {
    }

    private record AnswerKey(boolean correct) {
    }

    private record ExistingAnswer(String itemId, String label) {
    }

    private record ItemRow(String itemId, String question, int partNumber, String label, String text) {
    }
}
