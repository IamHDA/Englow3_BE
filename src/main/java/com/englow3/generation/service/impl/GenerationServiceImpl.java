package com.englow3.generation.service.impl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.generation.dto.result.GenerationAllowanceResult;
import com.englow3.generation.dto.result.GenerationRequestResult;
import com.englow3.generation.entity.GenerationKind;
import com.englow3.generation.entity.GenerationRequest;
import com.englow3.generation.entity.GenerationStatus;
import com.englow3.generation.repository.GenerationRequestRepository;
import com.englow3.generation.service.GenerationService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.time.StudyCalendar;
import com.englow3.user.api.UserDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Starting requests and showing them. Running their steps is {@link GenerationSteps}'s.
 * <p>
 * The limits span rows, so they live here rather than in the entity, checked under a per-user advisory lock: two
 * presses at the same moment would otherwise both see room for one more.
 */
@Service
@RequiredArgsConstructor
public class GenerationServiceImpl implements GenerationService {

    /** Requests a day per user. A failed request does not count (docs/module-map.md, `generation`). */
    public static final int DAILY_LIMIT = 3;
    private static final List<GenerationStatus> IN_PROGRESS = List.of(GenerationStatus.QUEUED,
            GenerationStatus.RUNNING);

    private final GenerationRequestRepository requestRepo;
    private final GenerationSteps steps;
    private final AiJobQueue queue;
    private final UserDirectory users;
    private final StudyCalendar calendar;
    private final Clock clock;
    private final ObjectMapper mapper;

    @Override
    @Transactional
    public GenerationRequestResult request(GenerationKind kind, String brief) {
        UUID userId = users.requireCurrentUserId();
        requestRepo.lockUser("generation:" + userId);
        if (requestRepo.existsByRequestedByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw new ConflictException("GENERATION_IN_PROGRESS",
                    "One of your requests is still running; wait for it to finish");
        }
        if (requestRepo.countCountedSince(userId, calendar.startOfToday()) >= DAILY_LIMIT) {
            throw new ConflictException("GENERATION_DAILY_LIMIT",
                    "You have used today's %d requests; failed ones do not count".formatted(DAILY_LIMIT));
        }
        GenerationRequest request = requestRepo.save(GenerationRequest.create(kind, userId, brief, clock.instant()));
        steps.enqueue(request);
        return toResult(request, users.displayNames(List.of(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GenerationRequestResult> list(Pageable page) {
        Page<GenerationRequest> requests = requestRepo.findAllByOrderByCreatedAtDesc(page);
        Map<UUID, String> names = users
                .displayNames(requests.getContent().stream().map(GenerationRequest::getRequestedByUserId).toList());
        return requests.map(request -> toResult(request, names));
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationRequestResult detail(UUID id) {
        GenerationRequest request = requestRepo.findById(id).orElseThrow(
                () -> new NotFoundException("GENERATION_REQUEST_NOT_FOUND", "No generation request %s".formatted(id)));
        return toResult(request, users.displayNames(List.of(request.getRequestedByUserId())));
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationAllowanceResult allowance() {
        UUID userId = users.requireCurrentUserId();
        long used = requestRepo.countCountedSince(userId, calendar.startOfToday());
        return new GenerationAllowanceResult(DAILY_LIMIT, used, Math.max(0, DAILY_LIMIT - used),
                requestRepo.existsByRequestedByUserIdAndStatusIn(userId, IN_PROGRESS));
    }

    private GenerationRequestResult toResult(GenerationRequest r, Map<UUID, String> names) {
        JsonNode components = read(r.getComponents());
        String title = components.path("plan").path("title").asText(null);
        List<UUID> taskIds = new ArrayList<>();
        read(r.getTaskIds()).forEach(id -> taskIds.add(UUID.fromString(id.asText())));
        List<String> problems = new ArrayList<>();
        read(r.getLastProblems()).forEach(p -> problems.add(p.asText()));
        return new GenerationRequestResult(r.getId(), r.getKind(), r.getStatus(), r.getStep(), r.getStepIndex(),
                GenerationRequest.STEPS.size(), r.getAttempt(), r.getBrief(), r.getRequestedByUserId(),
                names.get(r.getRequestedByUserId()), title, r.getExamId(), taskIds, r.getFullTestId(),
                r.getFailureCode(), r.getFailureMessage(), problems, r.getCreatedAt(), r.getUpdatedAt(),
                r.getCompletedAt());
    }

    private JsonNode read(String json) {
        try {
            return json == null ? mapper.createObjectNode() : mapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException unreadable) {
            return mapper.createObjectNode();
        }
    }
}
