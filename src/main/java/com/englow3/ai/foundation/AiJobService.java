package com.englow3.ai.foundation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.logging.TraceIdFilter;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class AiJobService {

    private final AiJobRepository repository;
    private final AiModelPolicyService policyService;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final AiJobEventPublisher eventPublisher;
    private final AiJobEventStream eventStream;

    AiJobService(AiJobRepository repository, AiModelPolicyService policyService, CurrentUser currentUser,
            UserRepository userRepository, AiJobEventPublisher eventPublisher, AiJobEventStream eventStream) {
        this.repository = repository;
        this.policyService = policyService;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.eventStream = eventStream;
    }

    @Transactional
    public AiJob submitForCurrentUser(AiCapability capability, String jobType, String targetType, UUID targetId,
            String promptVersion, JsonNode inputPayload, String idempotencyKey) {
        User user = requireCurrentUser();
        AiJob existing = repository.findByRequesterUserIdAndIdempotencyKey(user.getId(), idempotencyKey).orElse(null);
        if (existing != null) {
            if (existing.getCapability() != capability || !existing.getJobType().equals(jobType)
                    || !existing.getTargetType().equals(targetType) || !existing.getTargetId().equals(targetId)
                    || !existing.getPromptVersion().equals(promptVersion)
                    || !existing.getInputPayload().equals(inputPayload)) {
                throw new ConflictException("AI_JOB_IDEMPOTENCY_CONFLICT",
                        "The idempotency key was already used for a different AI request");
            }
            return existing;
        }
        return create(user.getId(), capability, jobType, targetType, targetId, promptVersion, inputPayload,
                idempotencyKey);
    }

    @Transactional(readOnly = true)
    public AiJob getForCurrentUser(UUID jobId) {
        UUID userId = requireCurrentUser().getId();
        return repository.findByIdAndRequesterUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("AI_JOB_NOT_FOUND", "AI job was not found"));
    }

    @Transactional
    public AiJob cancelForCurrentUser(UUID jobId) {
        AiJob job = getForCurrentUser(jobId);
        AiJobStatus before = job.getStatus();
        job.cancel(Instant.now());
        if (job.getStatus() == AiJobStatus.CANCELLED && before != AiJobStatus.CANCELLED) {
            eventPublisher.record(job, "CANCELLED");
        }
        return job;
    }

    @Transactional(readOnly = true)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter eventsForCurrentUser(long afterEventId) {
        return eventStream.subscribe(requireCurrentUser().getId(), Math.max(0, afterEventId));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(AiCapability capability) {
        return policyService.resolve(capability).enabled();
    }

    private AiJob create(UUID userId, AiCapability capability, String jobType, String targetType, UUID targetId,
            String promptVersion, JsonNode inputPayload, String idempotencyKey) {
        ResolvedAiPolicy policy = policyService.resolve(capability);
        if (!policy.enabled()) {
            throw new ConflictException("AI_CAPABILITY_DISABLED", "This AI capability is currently disabled");
        }
        AiJob job = repository
                .saveAndFlush(AiJob.pending(userId, capability, jobType, targetType, targetId, policy.provider(),
                        policy.model(), promptVersion, inputPayload, idempotencyKey, TraceIdFilter.current()));
        eventPublisher.record(job, "QUEUED");
        return job;
    }

    private User requireCurrentUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }
}
