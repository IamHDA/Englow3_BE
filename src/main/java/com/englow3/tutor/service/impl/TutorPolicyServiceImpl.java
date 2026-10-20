package com.englow3.tutor.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.error.*;
import com.englow3.tutor.dto.command.TutorPolicyCommand;
import com.englow3.tutor.dto.result.TutorPolicyResult;
import com.englow3.tutor.entity.TutorAudit;
import com.englow3.tutor.repository.TutorAuditRepository;
import com.englow3.tutor.repository.TutorPolicyRepository;
import com.englow3.tutor.service.TutorPolicyService;
import com.englow3.user.api.UserDirectory;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TutorPolicyServiceImpl implements TutorPolicyService {
    private final TutorPolicyRepository policyRepo;
    private final TutorAuditRepository auditRepo;
    private final UserDirectory users;
    private final CurrentUser currentUser;
    private final AiJobQueue jobs;
    @Value("${app.ai.queue-scope:local}")
    private String scope;
    @Value("${app.ai.enabled:false}")
    private boolean enabled;
    @Value("${app.ai.default-model:}")
    private String model;
    @Value("${app.tutor.streaming-enabled:false}")
    private boolean streaming;
    @Value("${app.tutor.voice-enabled:false}")
    private boolean voice;

    private void requireAdmin() {
        if (!currentUser.hasRole("ADMIN"))
            throw new ForbiddenException("ACCESS_DENIED", "Administrator access required");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean accepting() {
        return policyRepo.read(scope, false).accepting();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> allowedModes() {
        return policyRepo.read(scope, false).modes();
    }

    private TutorPolicyResult result(TutorPolicyRepository.Row row) {
        return new TutorPolicyResult(row.accepting(), row.modes(), row.version(), enabled, model,
                jobs.dailyRequestLimit(), streaming, voice);
    }

    @Override
    @Transactional(readOnly = true)
    public TutorPolicyResult view() {
        requireAdmin();
        return result(policyRepo.read(scope, false));
    }

    @Override
    @Transactional
    public TutorPolicyResult update(TutorPolicyCommand command) {
        requireAdmin();
        if (command.allowedModes() == null
                || command.allowedModes().stream()
                        .anyMatch(mode -> mode == null || !TutorPolicyRepository.MODES.contains(mode))
                || command.allowedModes().size() > 4 || (command.accepting() && command.allowedModes().isEmpty())
                || command.reason() == null || command.reason().strip().length() < 5 || command.reason().length() > 500)
            throw new BadRequestException("TUTOR_POLICY_INVALID", "Choose supported modes and explain this change");
        policyRepo.ensure(scope);
        var before = policyRepo.read(scope, true);
        if (before.version() != command.version())
            throw new ConflictException("TUTOR_POLICY_STALE", "Reload the latest policy before saving");
        policyRepo.save(scope, command.accepting(), command.allowedModes().stream().distinct().toList());
        auditRepo.save(TutorAudit.record(users.requireCurrentUserId(),
                UUID.nameUUIDFromBytes(("tutor-policy:" + scope).getBytes(StandardCharsets.UTF_8)),
                "TUTOR_POLICY_CHANGE", command.reason().strip()));
        return result(policyRepo.read(scope, false));
    }
}
