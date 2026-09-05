package com.englow3.ai.governance;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.foundation.AiCapability;
import com.englow3.ai.evaluation.AiEvaluationService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
class AiAdminService {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AiEvaluationService evaluationService;

    AiAdminService(JdbcTemplate jdbcTemplate, CurrentUser currentUser, UserRepository userRepository,
            ObjectMapper objectMapper, AiEvaluationService evaluationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.evaluationService = evaluationService;
    }

    @Transactional(readOnly = true)
    List<AiGovernanceDtos.PromptSummary> prompts() {
        return jdbcTemplate.query("""
                select t.id, t.template_key, t.description, v.version as active_version, t.updated_at
                from ai_prompt_templates t
                left join ai_prompt_versions v on v.template_id = t.id and v.active
                order by t.template_key
                """,
                (rs, row) -> new AiGovernanceDtos.PromptSummary(rs.getObject("id", UUID.class),
                        rs.getString("template_key"), rs.getString("description"),
                        rs.getObject("active_version", Integer.class), rs.getTimestamp("updated_at").toInstant()));
    }

    @Transactional
    AiGovernanceDtos.PromptSummary createPrompt(AiGovernanceDtos.PromptTemplateRequest request) {
        User actor = requireUser();
        UUID id = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    insert into ai_prompt_templates (id, template_key, description) values (?, ?, ?)
                    """, id, request.templateKey().strip(), request.description().strip());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ConflictException("AI_PROMPT_EXISTS", "A prompt with this key already exists");
        }
        audit(actor.getId(), "PROMPT_CREATE", "AI_PROMPT_TEMPLATE", id.toString(), null);
        return prompt(id);
    }

    @Transactional
    int createVersion(UUID templateId, AiGovernanceDtos.PromptVersionRequest request) {
        User actor = requireUser();
        UUID lockedId = jdbcTemplate.query("select id from ai_prompt_templates where id = ? for update",
                rs -> rs.next() ? rs.getObject("id", UUID.class) : null, templateId);
        if (lockedId == null) {
            throw new NotFoundException("AI_PROMPT_NOT_FOUND", "AI prompt template was not found");
        }
        Integer next = jdbcTemplate.queryForObject(
                "select coalesce(max(version), 0) + 1 from ai_prompt_versions where template_id = ?", Integer.class,
                templateId);
        jdbcTemplate.update("""
                insert into ai_prompt_versions
                    (id, template_id, version, system_template, user_template, response_schema, active, created_by)
                values (?, ?, ?, ?, ?, ?::jsonb, false, ?)
                """, UUID.randomUUID(), templateId, next, request.systemTemplate(), request.userTemplate(),
                json(request.responseSchema()), actor.getId());
        audit(actor.getId(), "PROMPT_VERSION_CREATE", "AI_PROMPT_TEMPLATE", templateId.toString(),
                objectMapper.createObjectNode().put("version", next));
        return next;
    }

    @Transactional
    void activateVersion(UUID templateId, int version, UUID evaluationRunId) {
        User actor = requireUser();
        Integer exists = jdbcTemplate.queryForObject("""
                select count(*) from ai_prompt_versions where template_id = ? and version = ?
                """, Integer.class, templateId, version);
        if (exists == null || exists == 0) {
            throw new NotFoundException("AI_PROMPT_VERSION_NOT_FOUND", "AI prompt version was not found");
        }
        evaluationService.requireAcceptedForPrompt(templateId, version, evaluationRunId);
        jdbcTemplate.update("update ai_prompt_versions set active = false where template_id = ? and active",
                templateId);
        jdbcTemplate.update("""
                update ai_prompt_versions set active = true, evaluation_run_id = ?
                where template_id = ? and version = ?
                """, evaluationRunId, templateId, version);
        audit(actor.getId(), "PROMPT_VERSION_ACTIVATE", "AI_PROMPT_TEMPLATE", templateId.toString(), objectMapper
                .createObjectNode().put("version", version).put("evaluationRunId", evaluationRunId.toString()));
    }

    @Transactional(readOnly = true)
    List<AiGovernanceDtos.ModelPolicyResponse> policies() {
        return jdbcTemplate.query("""
                select capability, provider_name, model_name, temperature, max_output_tokens,
                       input_cost_per_million, output_cost_per_million, enabled, evaluation_run_id, updated_at
                from ai_model_policies order by capability
                """,
                (rs, row) -> new AiGovernanceDtos.ModelPolicyResponse(rs.getString("capability"),
                        rs.getString("provider_name"), rs.getString("model_name"), rs.getBigDecimal("temperature"),
                        rs.getInt("max_output_tokens"), rs.getBigDecimal("input_cost_per_million"),
                        rs.getBigDecimal("output_cost_per_million"), rs.getBoolean("enabled"),
                        rs.getObject("evaluation_run_id", UUID.class), rs.getTimestamp("updated_at").toInstant()));
    }

    @Transactional
    AiGovernanceDtos.ModelPolicyResponse updatePolicy(AiCapability capability,
            AiGovernanceDtos.ModelPolicyRequest request) {
        User actor = requireUser();
        if (request.enabled()) {
            if (request.evaluationRunId() == null) {
                throw new ConflictException("AI_EVALUATION_REQUIRED",
                        "Enabling a model policy requires an accepted evaluation run");
            }
            evaluationService.requireAcceptedForModel(capability.name(), request.provider(), request.model(),
                    request.evaluationRunId());
        }
        jdbcTemplate.update("""
                insert into ai_model_policies
                    (capability, provider_name, model_name, temperature, max_output_tokens,
                     input_cost_per_million, output_cost_per_million, enabled, evaluation_run_id)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (capability) do update set
                    provider_name = excluded.provider_name, model_name = excluded.model_name,
                    temperature = excluded.temperature, max_output_tokens = excluded.max_output_tokens,
                    input_cost_per_million = excluded.input_cost_per_million,
                    output_cost_per_million = excluded.output_cost_per_million,
                    enabled = excluded.enabled, evaluation_run_id = excluded.evaluation_run_id
                """, capability.name(), request.provider(), request.model(), request.temperature(),
                request.maxOutputTokens(), request.inputCostPerMillion(), request.outputCostPerMillion(),
                request.enabled(), request.evaluationRunId());
        audit(actor.getId(), "MODEL_POLICY_UPDATE", "AI_CAPABILITY", capability.name(),
                objectMapper.createObjectNode().put("model", request.model()).put("enabled", request.enabled()).put(
                        "evaluationRunId",
                        request.evaluationRunId() == null ? null : request.evaluationRunId().toString()));
        return policy(capability);
    }

    @Transactional(readOnly = true)
    AiGovernanceDtos.AiOperationsMetrics metrics() {
        return jdbcTemplate.query(
                """
                        select count(*) as total_jobs,
                               count(*) filter (where status = 'SUCCEEDED') as successful_jobs,
                               count(*) filter (where status = 'FAILED') as failed_jobs,
                               count(*) filter (where status in ('PENDING', 'RETRY_SCHEDULED', 'PROCESSING')) as pending_jobs,
                               coalesce(sum(input_tokens), 0) as input_tokens,
                               coalesce(sum(output_tokens), 0) as output_tokens,
                               coalesce(sum(estimated_cost), 0) as estimated_cost,
                               (select count(*) from ai_feedback_reports where status in ('OPEN', 'INVESTIGATING')) as open_reports
                        from ai_jobs
                        """,
                rs -> {
                    rs.next();
                    return new AiGovernanceDtos.AiOperationsMetrics(rs.getLong("total_jobs"),
                            rs.getLong("successful_jobs"), rs.getLong("failed_jobs"), rs.getLong("pending_jobs"),
                            rs.getLong("input_tokens"), rs.getLong("output_tokens"),
                            value(rs.getBigDecimal("estimated_cost")), rs.getLong("open_reports"));
                });
    }

    private AiGovernanceDtos.PromptSummary prompt(UUID id) {
        return jdbcTemplate.query("""
                select t.id, t.template_key, t.description, v.version as active_version, t.updated_at
                from ai_prompt_templates t
                left join ai_prompt_versions v on v.template_id = t.id and v.active
                where t.id = ?
                """, rs -> {
            if (!rs.next()) {
                throw new NotFoundException("AI_PROMPT_NOT_FOUND", "AI prompt template was not found");
            }
            return new AiGovernanceDtos.PromptSummary(rs.getObject("id", UUID.class), rs.getString("template_key"),
                    rs.getString("description"), rs.getObject("active_version", Integer.class),
                    rs.getTimestamp("updated_at").toInstant());
        }, id);
    }

    private AiGovernanceDtos.ModelPolicyResponse policy(AiCapability capability) {
        return jdbcTemplate.query("""
                select capability, provider_name, model_name, temperature, max_output_tokens,
                       input_cost_per_million, output_cost_per_million, enabled, evaluation_run_id, updated_at
                from ai_model_policies where capability = ?
                """, rs -> {
            if (!rs.next()) {
                throw new NotFoundException("AI_MODEL_POLICY_NOT_FOUND", "AI model policy was not found");
            }
            return new AiGovernanceDtos.ModelPolicyResponse(rs.getString("capability"), rs.getString("provider_name"),
                    rs.getString("model_name"), rs.getBigDecimal("temperature"), rs.getInt("max_output_tokens"),
                    rs.getBigDecimal("input_cost_per_million"), rs.getBigDecimal("output_cost_per_million"),
                    rs.getBoolean("enabled"), rs.getObject("evaluation_run_id", UUID.class),
                    rs.getTimestamp("updated_at").toInstant());
        }, capability.name());
    }

    private void audit(UUID actor, String action, String targetType, String targetId, JsonNode details) {
        jdbcTemplate.update("""
                insert into ai_admin_audit_log (id, actor_user_id, action, target_type, target_id, details)
                values (?, ?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), actor, action, targetType, targetId,
                json(details == null ? objectMapper.createObjectNode() : details));
    }

    private String json(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize AI administration data", ex);
        }
    }

    private BigDecimal value(BigDecimal input) {
        return input == null ? BigDecimal.ZERO : input;
    }

    private User requireUser() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"));
    }
}
