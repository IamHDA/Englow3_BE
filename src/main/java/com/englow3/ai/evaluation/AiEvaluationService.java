package com.englow3.ai.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.ai.evaluation.AiEvaluationDtos.CaseRequest;
import com.englow3.ai.evaluation.AiEvaluationDtos.DecisionRequest;
import com.englow3.ai.evaluation.AiEvaluationDtos.RunRequest;
import com.englow3.ai.evaluation.AiEvaluationDtos.RunResponse;
import com.englow3.ai.evaluation.AiEvaluationDtos.SuiteRequest;
import com.englow3.ai.evaluation.AiEvaluationDtos.SuiteResponse;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AiEvaluationService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    AiEvaluationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, CurrentUser currentUser,
            UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    @Transactional
    public SuiteResponse createSuite(SuiteRequest request) {
        UUID actor = actorId();
        validateCases(request.cases());
        UUID suiteId = UUID.randomUUID();
        String suiteHash = hash(objectMapper.valueToTree(request).toString());
        try {
            jdbcTemplate.update("""
                    insert into ai_evaluation_suites
                        (id, suite_key, version, capability, repetitions, schema_success_min,
                         evidence_fidelity_min, unsafe_rate_max, score_variance_max, human_agreement_min,
                         latency_p95_max_ms, suite_hash, created_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, suiteId, request.suiteKey().strip(), request.version(), request.capability().name(),
                    request.repetitions(), request.schemaSuccessMin(), request.evidenceFidelityMin(),
                    request.unsafeRateMax(), request.scoreVarianceMax(), request.humanAgreementMin(),
                    request.latencyP95MaxMs(), suiteHash, actor);
            for (CaseRequest evaluationCase : request.cases()) {
                String caseHash = hash(evaluationCase.promptVariables().toString() + "\n"
                        + evaluationCase.expectedContract().toString());
                jdbcTemplate.update("""
                        insert into ai_evaluation_cases
                            (id, suite_id, case_key, prompt_variables, expected_contract, case_hash)
                        values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
                        """, UUID.randomUUID(), suiteId, evaluationCase.caseKey().strip(),
                        evaluationCase.promptVariables().toString(), evaluationCase.expectedContract().toString(),
                        caseHash);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("AI_EVALUATION_SUITE_EXISTS",
                    "An evaluation suite with this version or hash already exists");
        }
        return suite(suiteId);
    }

    @Transactional(readOnly = true)
    public List<SuiteResponse> suites() {
        return jdbcTemplate.query("""
                select s.id, s.suite_key, s.version, s.capability, s.repetitions, s.suite_hash, s.created_at,
                       count(c.id) case_count
                from ai_evaluation_suites s left join ai_evaluation_cases c on c.suite_id = s.id
                group by s.id order by s.suite_key, s.version desc
                """, (rs, row) -> mapSuite(rs));
    }

    @Transactional
    public RunResponse createRun(RunRequest request) {
        UUID actor = actorId();
        String capability = jdbcTemplate.query("select capability from ai_evaluation_suites where id = ?",
                rs -> rs.next() ? rs.getString("capability") : null, request.suiteId());
        if (capability == null) {
            throw new NotFoundException("AI_EVALUATION_SUITE_NOT_FOUND", "Evaluation suite was not found");
        }
        String templateKey = jdbcTemplate.query("""
                select t.template_key from ai_prompt_versions v
                join ai_prompt_templates t on t.id = v.template_id
                where v.template_id = ? and v.version = ?
                """, rs -> rs.next() ? rs.getString("template_key") : null, request.promptTemplateId(),
                request.promptVersion());
        if (templateKey == null) {
            throw new NotFoundException("AI_EVALUATION_PROMPT_NOT_FOUND", "Evaluation prompt version was not found");
        }
        if (!expectedTemplate(capability).equals(templateKey)) {
            throw new BadRequestException("AI_EVALUATION_CAPABILITY_MISMATCH",
                    "Evaluation suite capability does not match the prompt template");
        }
        if (request.baselineRunId() != null) {
            requireAcceptedRun(request.baselineRunId(), request.suiteId());
        }
        String candidateHash = hash(objectMapper.valueToTree(request).toString());
        UUID candidateId = jdbcTemplate.query("""
                select id from ai_evaluation_candidates where suite_id = ? and candidate_hash = ?
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null, request.suiteId(), candidateHash);
        if (candidateId == null) {
            candidateId = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into ai_evaluation_candidates
                        (id, suite_id, provider_name, model_name, prompt_template_id, prompt_version,
                         temperature, max_output_tokens, input_cost_per_million, output_cost_per_million,
                         baseline_run_id, candidate_hash, created_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, candidateId, request.suiteId(), request.provider().strip(), request.model().strip(),
                    request.promptTemplateId(), request.promptVersion(), request.temperature(),
                    request.maxOutputTokens(), request.inputCostPerMillion(), request.outputCostPerMillion(),
                    request.baselineRunId(), candidateHash, actor);
        }
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("insert into ai_evaluation_runs (id, candidate_id, status) values (?, ?, 'PENDING')", runId,
                candidateId);
        audit(actor, "EVALUATION_RUN_CREATE", runId, objectMapper.createObjectNode().put("capability", capability));
        return run(runId);
    }

    @Transactional(readOnly = true)
    public List<RunResponse> runs() {
        return jdbcTemplate.query("""
                select id, candidate_id, status, hard_gates_passed, human_quality_passed, summary,
                       failure_code, decision_reason, created_at, completed_at, decided_at
                from ai_evaluation_runs order by created_at desc limit 200
                """, (rs, row) -> mapRun(rs));
    }

    @Transactional(readOnly = true)
    public RunResponse run(UUID runId) {
        RunResponse result = jdbcTemplate.query("""
                select id, candidate_id, status, hard_gates_passed, human_quality_passed, summary,
                       failure_code, decision_reason, created_at, completed_at, decided_at
                from ai_evaluation_runs where id = ?
                """, rs -> rs.next() ? mapRun(rs) : null, runId);
        if (result == null) {
            throw new NotFoundException("AI_EVALUATION_RUN_NOT_FOUND", "Evaluation run was not found");
        }
        return result;
    }

    @Transactional
    public RunResponse decide(UUID runId, DecisionRequest request) {
        UUID actor = actorId();
        RunResponse run = run(runId);
        if (!"AWAITING_HUMAN".equals(run.status()) || !Boolean.TRUE.equals(run.hardGatesPassed())) {
            throw new ConflictException("AI_EVALUATION_NOT_DECIDABLE",
                    "Only hard-gate-passing runs awaiting human review can be decided");
        }
        jdbcTemplate.update("""
                update ai_evaluation_runs
                set status = ?, human_quality_passed = ?, decided_by = ?, decision_reason = ?, decided_at = now()
                where id = ?
                """, request.accepted() ? "ACCEPTED" : "REJECTED", request.accepted(), actor, request.reason().strip(),
                runId);
        audit(actor, request.accepted() ? "EVALUATION_ACCEPT" : "EVALUATION_REJECT", runId,
                objectMapper.createObjectNode().put("reason", request.reason().strip()));
        return run(runId);
    }

    @Transactional
    public EvaluationWork claimNext() {
        jdbcTemplate.update("""
                update ai_evaluation_runs set status = 'PENDING', retry_count = retry_count + 1,
                    started_at = null, failure_code = 'AI_EVALUATION_WORKER_EXPIRED'
                where status = 'RUNNING' and started_at < now() - interval '6 hours' and retry_count < 3
                """);
        jdbcTemplate.update("""
                update ai_evaluation_runs set status = 'FAILED', hard_gates_passed = false,
                    completed_at = now(), failure_code = 'AI_EVALUATION_RETRY_EXHAUSTED'
                where status = 'RUNNING' and started_at < now() - interval '6 hours' and retry_count >= 3
                """);
        UUID runId = jdbcTemplate.query("""
                select id from ai_evaluation_runs where status = 'PENDING'
                order by created_at for update skip locked limit 1
                """, rs -> rs.next() ? rs.getObject("id", UUID.class) : null);
        if (runId == null) {
            return null;
        }
        jdbcTemplate.update("delete from ai_evaluation_case_results where run_id = ?", runId);
        jdbcTemplate.update("update ai_evaluation_runs set status = 'RUNNING', started_at = now() where id = ?", runId);
        EvaluationCandidate candidate = jdbcTemplate.query("""
                select r.id run_id, c.id candidate_id, c.provider_name, c.model_name, c.temperature,
                       c.max_output_tokens, c.input_cost_per_million, c.output_cost_per_million,
                       c.baseline_run_id, s.repetitions, s.schema_success_min, s.evidence_fidelity_min,
                       s.unsafe_rate_max, s.score_variance_max, s.human_agreement_min, s.latency_p95_max_ms,
                       p.system_template, p.user_template
                from ai_evaluation_runs r
                join ai_evaluation_candidates c on c.id = r.candidate_id
                join ai_evaluation_suites s on s.id = c.suite_id
                join ai_prompt_versions p on p.template_id = c.prompt_template_id
                    and p.version = c.prompt_version
                where r.id = ?
                """, rs -> {
            rs.next();
            return new EvaluationCandidate(runId, rs.getString("provider_name"), rs.getString("model_name"),
                    rs.getBigDecimal("temperature"), rs.getInt("max_output_tokens"),
                    rs.getBigDecimal("input_cost_per_million"), rs.getBigDecimal("output_cost_per_million"),
                    rs.getObject("baseline_run_id", UUID.class), rs.getInt("repetitions"),
                    rs.getBigDecimal("schema_success_min"), rs.getBigDecimal("evidence_fidelity_min"),
                    rs.getBigDecimal("unsafe_rate_max"), rs.getBigDecimal("score_variance_max"),
                    rs.getBigDecimal("human_agreement_min"), rs.getInt("latency_p95_max_ms"),
                    rs.getString("system_template"), rs.getString("user_template"));
        }, runId);
        List<EvaluationCase> cases = jdbcTemplate.query("""
                select ec.id, ec.case_key, ec.prompt_variables, ec.expected_contract
                from ai_evaluation_cases ec join ai_evaluation_candidates c on c.suite_id = ec.suite_id
                where c.id = ? order by ec.case_key
                """,
                (rs, row) -> new EvaluationCase(rs.getObject("id", UUID.class), rs.getString("case_key"),
                        jsonNode(rs.getString("prompt_variables")), jsonNode(rs.getString("expected_contract"))),
                candidateId(candidate.runId()));
        return new EvaluationWork(candidate, cases);
    }

    @Transactional
    public void saveResult(UUID runId, UUID caseId, int attempt, EvaluationCaseResult result) {
        jdbcTemplate.update("""
                insert into ai_evaluation_case_results
                    (run_id, case_id, attempt, schema_success, evidence_fidelity, unsafe_response,
                     automatic_score, score_delta, latency_ms, input_tokens, output_tokens,
                     estimated_cost, output_hash, violations)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                """, runId, caseId, attempt, result.schemaSuccess(), result.evidenceFidelity(), result.unsafeResponse(),
                result.automaticScore(), result.scoreDelta(), result.latencyMs(), result.inputTokens(),
                result.outputTokens(), result.estimatedCost(), result.outputHash(), result.violations().toString());
    }

    @Transactional
    public void finish(UUID runId, JsonNode summary, boolean hardPassed) {
        jdbcTemplate.update("""
                update ai_evaluation_runs
                set status = ?, summary = cast(? as jsonb), hard_gates_passed = ?, completed_at = now()
                where id = ? and status = 'RUNNING'
                """, hardPassed ? "AWAITING_HUMAN" : "REJECTED", summary.toString(), hardPassed, runId);
    }

    @Transactional
    public void fail(UUID runId, String code) {
        jdbcTemplate.update("""
                update ai_evaluation_runs set status = 'FAILED', hard_gates_passed = false,
                    failure_code = ?, completed_at = now() where id = ? and status = 'RUNNING'
                """, code, runId);
    }

    @Transactional(readOnly = true)
    public JsonNode baselineSummary(UUID runId) {
        if (runId == null) {
            return null;
        }
        return jdbcTemplate.query("select summary from ai_evaluation_runs where id = ? and status = 'ACCEPTED'",
                rs -> rs.next() ? jsonNode(rs.getString("summary")) : null, runId);
    }

    @Transactional(readOnly = true)
    public void requireAcceptedForPrompt(UUID templateId, int version, UUID runId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from ai_evaluation_runs r
                join ai_evaluation_candidates c on c.id = r.candidate_id
                where r.id = ? and r.status = 'ACCEPTED' and r.hard_gates_passed
                  and r.human_quality_passed and c.prompt_template_id = ? and c.prompt_version = ?
                """, Integer.class, runId, templateId, version);
        if (count == null || count == 0) {
            throw new ConflictException("AI_EVALUATION_GATE_FAILED",
                    "An accepted evaluation for this prompt version is required");
        }
    }

    @Transactional(readOnly = true)
    public void requireAcceptedForModel(String capability, String provider, String model, UUID runId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from ai_evaluation_runs r
                join ai_evaluation_candidates c on c.id = r.candidate_id
                join ai_evaluation_suites s on s.id = c.suite_id
                where r.id = ? and r.status = 'ACCEPTED' and r.hard_gates_passed
                  and r.human_quality_passed and s.capability = ?
                  and c.provider_name = ? and c.model_name = ?
                """, Integer.class, runId, capability, provider, model);
        if (count == null || count == 0) {
            throw new ConflictException("AI_EVALUATION_GATE_FAILED",
                    "An accepted evaluation for this model policy is required");
        }
    }

    private void validateCases(List<CaseRequest> cases) {
        for (CaseRequest evaluationCase : cases) {
            if (!evaluationCase.promptVariables().isObject() || !evaluationCase.expectedContract().isObject()) {
                throw new BadRequestException("AI_EVALUATION_CASE_INVALID",
                        "Evaluation prompt variables and expected contract must be objects");
            }
        }
    }

    private SuiteResponse suite(UUID suiteId) {
        return jdbcTemplate.query("""
                select s.id, s.suite_key, s.version, s.capability, s.repetitions, s.suite_hash, s.created_at,
                       count(c.id) case_count
                from ai_evaluation_suites s left join ai_evaluation_cases c on c.suite_id = s.id
                where s.id = ? group by s.id
                """, rs -> {
            rs.next();
            return mapSuite(rs);
        }, suiteId);
    }

    private SuiteResponse mapSuite(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SuiteResponse(rs.getObject("id", UUID.class), rs.getString("suite_key"), rs.getInt("version"),
                rs.getString("capability"), rs.getInt("repetitions"), rs.getString("suite_hash"),
                rs.getInt("case_count"), rs.getTimestamp("created_at").toInstant());
    }

    private RunResponse mapRun(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RunResponse(rs.getObject("id", UUID.class), rs.getObject("candidate_id", UUID.class),
                rs.getString("status"), rs.getObject("hard_gates_passed", Boolean.class),
                rs.getObject("human_quality_passed", Boolean.class), jsonNode(rs.getString("summary")),
                rs.getString("failure_code"), rs.getString("decision_reason"),
                rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("completed_at")),
                instant(rs.getTimestamp("decided_at")));
    }

    private UUID candidateId(UUID runId) {
        return jdbcTemplate.queryForObject("select candidate_id from ai_evaluation_runs where id = ?", UUID.class,
                runId);
    }

    private void requireAcceptedRun(UUID runId, UUID suiteId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from ai_evaluation_runs r
                join ai_evaluation_candidates c on c.id = r.candidate_id
                where r.id = ? and r.status = 'ACCEPTED' and c.suite_id = ?
                """, Integer.class, runId, suiteId);
        if (count == null || count == 0) {
            throw new ConflictException("AI_EVALUATION_BASELINE_INVALID", "Baseline run must be accepted");
        }
    }

    private String expectedTemplate(String capability) {
        return switch (capability) {
            case "TUTOR" -> "TUTOR_REPLY";
            case "PLACEMENT" -> "PLACEMENT_REPORT";
            case "LEARNING_PATH" -> "LEARNING_PATH_EXPLANATION";
            case "SPEAKING" -> "SPEAKING_LANGUAGE_FEEDBACK";
            case "WRITING" -> "WRITING_ASSESSMENT";
            case "CONTENT_GENERATION" -> "CONTENT_DRAFT_GENERATION";
            default -> throw new BadRequestException("AI_EVALUATION_CAPABILITY_UNSUPPORTED",
                    "Evaluation capability is not supported");
        };
    }

    private JsonNode jsonNode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored evaluation JSON is invalid", exception);
        }
    }

    private UUID actorId() {
        return userRepository.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No internal user is linked to this token"))
                .getId();
    }

    private void audit(UUID actor, String action, UUID runId, JsonNode details) {
        jdbcTemplate.update("""
                insert into ai_admin_audit_log (id, actor_user_id, action, target_type, target_id, details)
                values (?, ?, ?, 'AI_EVALUATION_RUN', ?, cast(? as jsonb))
                """, UUID.randomUUID(), actor, action, runId.toString(), details.toString());
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record EvaluationWork(EvaluationCandidate candidate, List<EvaluationCase> cases) {
    }

    public record EvaluationCandidate(UUID runId, String provider, String model, java.math.BigDecimal temperature,
            int maxOutputTokens, java.math.BigDecimal inputCost, java.math.BigDecimal outputCost, UUID baselineRunId,
            int repetitions, java.math.BigDecimal schemaMin, java.math.BigDecimal evidenceMin,
            java.math.BigDecimal unsafeMax, java.math.BigDecimal varianceMax, java.math.BigDecimal agreementMin,
            int latencyP95MaxMs, String systemTemplate, String userTemplate) {
    }

    public record EvaluationCase(UUID id, String key, JsonNode variables, JsonNode expected) {
    }

    public record EvaluationCaseResult(boolean schemaSuccess, java.math.BigDecimal evidenceFidelity,
            boolean unsafeResponse, java.math.BigDecimal automaticScore, java.math.BigDecimal scoreDelta, int latencyMs,
            int inputTokens, int outputTokens, java.math.BigDecimal estimatedCost, String outputHash,
            JsonNode violations) {
    }
}
