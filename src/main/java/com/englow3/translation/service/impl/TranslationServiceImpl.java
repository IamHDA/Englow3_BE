package com.englow3.translation.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.translation.dto.command.SubmitTranslationCommand;
import com.englow3.translation.dto.result.TranslationAttemptResult;
import com.englow3.translation.dto.result.TranslationSetDetailResult;
import com.englow3.translation.dto.result.TranslationSetSummaryResult;
import com.englow3.translation.entity.TranslationAttempt;
import com.englow3.translation.entity.TranslationAttemptStatus;
import com.englow3.translation.entity.TranslationItem;
import com.englow3.translation.entity.TranslationSet;
import com.englow3.translation.entity.TranslationSetStatus;
import com.englow3.translation.helper.TranslationRubric;
import com.englow3.translation.repository.TranslationAttemptRepository;
import com.englow3.translation.repository.TranslationItemRepository;
import com.englow3.translation.repository.TranslationSetRepository;
import com.englow3.translation.service.TranslationService;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TranslationServiceImpl implements TranslationService {

    /** A person types a sentence in well over five seconds; more than this a minute is a script or a stuck retry. */
    static final int MAX_SUBMISSIONS_PER_WINDOW = 12;
    static final Duration SUBMISSION_WINDOW = Duration.ofMinutes(1);

    static final String ATTEMPT_TARGET_TYPE = "TRANSLATION_ATTEMPT";

    private final TranslationSetRepository setRepo;
    private final TranslationItemRepository itemRepo;
    private final TranslationAttemptRepository attemptRepo;
    private final UserDirectory userDirectory;
    private final UserRateLimiter rateLimiter;
    private final AiJobQueue aiJobQueue;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Page<TranslationSetSummaryResult> searchSets(String topic, String title, Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Page<TranslationSet> page = setRepo.searchByStatus(TranslationSetStatus.PUBLISHED, blankToNull(topic),
                blankToNull(title), pageable);
        List<UUID> ids = page.map(TranslationSet::getId).toList();
        Map<UUID, Long> items = ids.isEmpty() ? Map.of() : counts(itemRepo.countBySetIds(ids));
        Map<UUID, Long> graded = ids.isEmpty() ? Map.of() : counts(attemptRepo.gradedItemsBySet(userId, ids));
        return page.map(set -> TranslationSetSummaryResult.of(set, items.getOrDefault(set.getId(), 0L),
                graded.getOrDefault(set.getId(), 0L)));
    }

    @Override
    @Transactional(readOnly = true)
    public TranslationSetDetailResult setDetail(UUID setId) {
        UUID userId = userDirectory.requireCurrentUserId();
        TranslationSet set = requirePublished(setId);
        List<TranslationItem> items = itemRepo.findByTranslationSetIdOrderByOrderNo(setId);
        Map<UUID, TranslationAttempt> latest = attemptRepo.latestPerItem(userId, setId).stream()
                .collect(Collectors.toMap(TranslationAttempt::getTranslationItemId, Function.identity()));
        long graded = latest.values().stream().filter(TranslationAttempt::isGraded).count();
        return new TranslationSetDetailResult(TranslationSetSummaryResult.of(set, items.size(), graded),
                items.stream().map(item -> {
                    TranslationAttempt attempt = latest.get(item.getId());
                    return new TranslationSetDetailResult.Item(item.getId(), item.getOrderNo(), item.getSourceVi(),
                            attempt == null ? null
                                    : new TranslationSetDetailResult.LatestAttempt(attempt.getId(), attempt.getStatus(),
                                            attempt.getScore()));
                }).toList());
    }

    /**
     * The attempt and its grading job are written in one transaction: if today's AI allowance is spent, the queue
     * refuses and nothing is recorded, rather than leaving an attempt that will wait for a grade forever.
     */
    @Override
    @Transactional
    public TranslationAttemptResult submit(SubmitTranslationCommand command) {
        UUID userId = userDirectory.requireCurrentUserId();
        TranslationItem item = itemRepo.findById(command.itemId()).orElseThrow(() -> itemNotFound(command.itemId()));
        // The same refusal for "not published" and "not there": an id must not reveal which drafts exist.
        requirePublishedOr(item.getTranslationSetId(), () -> itemNotFound(command.itemId()));
        rateLimiter.check(userId, "translation-submit", MAX_SUBMISSIONS_PER_WINDOW, SUBMISSION_WINDOW);

        TranslationAttempt attempt = attemptRepo.save(TranslationAttempt.queued(userId, item.getTranslationSetId(),
                item.getId(), command.response(), clock.instant()));
        aiJobQueue.enqueueTranslationAssessment(attempt.getId(), payload(attempt, item),
                ATTEMPT_TARGET_TYPE + ":" + attempt.getId(), TranslationRubric.VERSION, userId);
        return result(attempt, item);
    }

    @Override
    @Transactional(readOnly = true)
    public TranslationAttemptResult attempt(UUID attemptId) {
        UUID userId = userDirectory.requireCurrentUserId();
        TranslationAttempt attempt = attemptRepo.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new NotFoundException("TRANSLATION_ATTEMPT_NOT_FOUND",
                        "No translation attempt with id %s".formatted(attemptId)));
        TranslationItem item = itemRepo.findById(attempt.getTranslationItemId())
                .orElseThrow(() -> itemNotFound(attempt.getTranslationItemId()));
        return result(attempt, item);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TranslationAttemptResult> history(Pageable pageable) {
        UUID userId = userDirectory.requireCurrentUserId();
        Page<TranslationAttempt> page = attemptRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        Collection<UUID> itemIds = page.map(TranslationAttempt::getTranslationItemId).toSet();
        Map<UUID, TranslationItem> items = itemRepo.findAllById(itemIds).stream()
                .collect(Collectors.toMap(TranslationItem::getId, Function.identity()));
        return page.map(attempt -> result(attempt, items.get(attempt.getTranslationItemId())));
    }

    private TranslationAttemptResult result(TranslationAttempt attempt, TranslationItem item) {
        boolean answered = attempt.getStatus() != TranslationAttemptStatus.QUEUED;
        return new TranslationAttemptResult(attempt.getId(), attempt.getTranslationSetId(),
                attempt.getTranslationItemId(), item.getSourceVi(), attempt.getResponse(), attempt.getStatus(),
                attempt.getScore(), feedback(attempt.getFeedback()), attempt.getErrorCode(),
                answered ? item.getReferenceEn() : null, answered ? item.getNotes() : null, attempt.getCreatedAt(),
                attempt.getGradedAt());
    }

    /** The stored report is the rubric's own validated output, so a field missing here is a bug, not bad input. */
    private TranslationAttemptResult.Feedback feedback(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode report = mapper.readTree(json);
            JsonNode parts = report.path("parts");
            List<TranslationAttemptResult.Mistake> errors = new java.util.ArrayList<>();
            for (JsonNode error : report.path("errors")) {
                errors.add(new TranslationAttemptResult.Mistake(error.path("type").asText(),
                        error.path("original").asText(), error.path("correction").asText(),
                        error.path("explanation").asText()));
            }
            return new TranslationAttemptResult.Feedback(report.path("verdict").asText(),
                    new TranslationAttemptResult.Parts(parts.path("meaning").asInt(), parts.path("grammar").asInt(),
                            parts.path("vocabulary").asInt(), parts.path("naturalness").asInt()),
                    report.path("summary").asText(), report.path("improved").asText(), errors);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored translation feedback is not JSON", e);
        }
    }

    private String payload(TranslationAttempt attempt, TranslationItem item) {
        try {
            return mapper
                    .writeValueAsString(Map.of("attemptId", attempt.getId().toString(), "sourceVi", item.getSourceVi(),
                            "referenceEn", item.getReferenceEn(), "learnerTranslation", attempt.getResponse()));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Could not serialise a grading request", impossible);
        }
    }

    private TranslationSet requirePublished(UUID setId) {
        return requirePublishedOr(setId, () -> new NotFoundException("TRANSLATION_SET_NOT_FOUND",
                "No translation set with id %s".formatted(setId)));
    }

    private TranslationSet requirePublishedOr(UUID setId,
            java.util.function.Supplier<? extends RuntimeException> missing) {
        return setRepo.findById(setId).filter(TranslationSet::isPublished).orElseThrow(missing);
    }

    private static NotFoundException itemNotFound(UUID itemId) {
        return new NotFoundException("TRANSLATION_ITEM_NOT_FOUND",
                "No translation sentence with id %s".formatted(itemId));
    }

    private static Map<UUID, Long> counts(List<TranslationItemRepository.SetCount> rows) {
        return rows.stream().collect(Collectors.toMap(TranslationItemRepository.SetCount::getSetId,
                TranslationItemRepository.SetCount::getTotal));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
