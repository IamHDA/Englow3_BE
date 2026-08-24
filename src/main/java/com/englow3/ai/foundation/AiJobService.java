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

    AiJobService(AiJobRepository repository, AiModelPolicyService policyService, CurrentUser currentUser,
            UserRepository userRepository) {
        this.repository = repository;
        this.policyService = policyService;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    @Transactional
    public AiJob submitForCurrentUser(AiCapability capability, String jobType, String targetType, UUID targetId,
            String promptVersion, JsonNode inputPayload, String idempotencyKey) {
        User user = requireCurrentUser();
        return repository.findByRequesterUserIdAndIdempotencyKey(user.getId(), idempotencyKey)
                .orElseGet(() -> create(user.getId(), capability, jobType, targetType, targetId, promptVersion,
                        inputPayload, idempotencyKey));
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
        job.cancel(Instant.now());
        return job;
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
        return repository.save(AiJob.pending(userId, capability, jobType, targetType, targetId, policy.provider(),
                policy.model(), promptVersion, inputPayload, idempotencyKey, TraceIdFilter.current()));
    }

    private User requireCurrentUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }
}
