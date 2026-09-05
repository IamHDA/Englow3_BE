# AI production completion plan

> Implementation status: all repository-code work in this plan is implemented through migration `V270` on the
> stacked feature branches. See `docs/AI_IMPLEMENTATION_STATUS.md` for branch order, verification evidence and
> external production gates that still require authorized human/infrastructure work.

Updated: 2026-08-24

## Target architecture

Spring Boot owns authenticated public APIs, authorization, learner state, transactions, content lifecycle,
job orchestration, prompt/model policy, usage accounting, and persistence. The FastAPI AI service owns
provider credentials, provider-specific HTTP contracts, inference normalization, and provider error
classification. Generated content is never published without deterministic validation and an authorized
human review decision.

```text
Web client
  -> Spring Boot public API
     -> domain transaction / AI job / policy / quota
        -> FastAPI internal API
           -> LLM or speech provider
        <- normalized provider response
     -> deterministic schema/evidence validation
     -> domain persistence / notification / audit
```

## Branch and dependency order

Every branch is based on the branch immediately above it so its migration and contract dependencies are
explicit. Each branch must pass Java tests, FastAPI tests, Flyway validation, formatting, static checks,
and relevant Docker builds before it is pushed.

1. `feat/ai-writing-assessment`
2. `feat/ai-content-publication`
3. `feat/ai-personalized-exams`
4. `feat/ai-adaptive-learning`
5. `feat/ai-semantic-tutor`
6. `feat/ai-speaking-coach`
7. `feat/ai-realtime-operations`
8. `feat/ai-evaluation`

## Cross-cutting definition of done

- Public endpoints authenticate users and enforce ownership or staff/admin authorization.
- Every mutating request is idempotent; reusing a key with a different payload returns a conflict.
- Provider credentials and provider response bodies never appear in public errors or logs.
- External calls have connect/read timeouts, bounded payloads, classified retry behavior, and a terminal
  business-state reconciliation path.
- Model output is treated as untrusted input and validated before persistence.
- Scores, answer keys, publication state, and authorization decisions are deterministic backend decisions,
  not model decisions.
- Prompts are versioned, model selection is policy-driven, and usage/cost is attributable to a capability.
- Database constraints protect invariants independently of Java validation.
- Tests include success, malformed model output, missing fields, unknown fields where contracts are strict,
  boundary values, ownership, idempotency conflicts, retries, cancellation, and terminal failure recovery.
- Operational metrics expose count, failure rate, retry rate, latency, token usage, and estimated cost without
  exposing learner content.

## 1. Writing assessment

### Runtime contract

- List only human-approved writing tasks.
- Accept a bounded learner response and an idempotency key.
- Resolve the task rubric and active prompt version inside the submission transaction.
- Queue `WRITING_ASSESSMENT`; polling uses the shared AI job endpoint and writing result endpoint.
- Require one score for every rubric dimension and reject unknown or duplicate dimensions.
- Require evidence to be exact text from the learner response.
- Compute the weighted overall score in Java; never accept a model-provided overall score.
- Persist CEFR estimate, criterion feedback, strengths, improvements, corrected response, sample revision,
  model/provider provenance, and raw normalized result.
- Reconcile cancelled or terminally failed jobs to `FAILED`.

### Required tests

- Weighted score calculation and rounding.
- Missing, duplicate, or unknown rubric criteria.
- Score outside 0..100 and invalid rubric weight.
- Fabricated evidence, empty feedback, empty recommendations, and invalid CEFR.
- Submission size, task approval, ownership, history, and idempotency conflict.
- Flyway constraints and prompt/model-policy seed.

## 2. AI content publication

### Runtime contract

- Replace the generic `items[]` check with a validator selected by content type.
- Validate Quiz answer cardinality, distractor rationale, level, taxonomy links, and duplicate fingerprints.
- Validate Dictation transcript, segment order/timestamps, audio metadata, and concept links.
- Validate Flashcard sense identity, definitions, examples, IPA/source status, and concept links.
- Validate Grammar title/theory/form/exercise shape and concept links.
- Store immutable draft revisions and validation reports.
- `submit-review` requires a successful validation report.
- Approval and publication are separate decisions with actor, reason, timestamp, and audit record.
- Publication materializes domain rows in one transaction and records their IDs on the draft revision.
- A failed materialization rolls back without partially published content.
- Archival creates a new state transition; it does not delete audit history.

### Required tests

- Validator matrix per content type, duplicate detection, taxonomy failures, invalid answer distributions,
  unauthorized state transitions, concurrent approval, transaction rollback, and repeat publication.

## 3. Personalized exams

### Runtime contract

- Input includes certificate, section/skill, target level, difficulty range, length, and optional weak concepts.
- Candidate content must be approved and cannot expose prior answer history to the learner-facing payload.
- A deterministic blueprint builder selects items; AI may propose metadata/explanations but cannot choose answer keys.
- Enforce part quotas, unique items, unique groups, media availability, difficulty distribution, concept coverage,
  and leakage rules before materializing an exam version.
- Persist blueprint, source item IDs, generation job, validation report, and review status.
- Only reviewed versions become available to the exam-attempt APIs.

### Required tests

- Every supported blueprint, insufficient candidate pools, duplicate groups/items, unavailable media,
  deterministic rebuild with seed, concurrent generation, and delivery-package validation.

## 4. Adaptive learning and placement

### Runtime contract

- Recommendations reference actual approved Flashcard, Grammar, Shadowing, Writing, Speaking, or Exam content,
  not only a concept ID.
- Each completion event has source identity, outcome, difficulty, duration, and deduplication key.
- BKT updates are deterministic and preserve an auditable prior/posterior event trail.
- Support complete, skip, postpone, replace, regenerate, and automatic path refresh.
- Recommendation ranking accounts for prerequisites, mastery, recency, learner priorities, target deadline,
  content availability, and repetition limits.
- Adaptive placement selects the next item by information value only when calibrated IRT parameters meet a
  minimum-response policy; otherwise it falls back to the fixed reviewed test.
- Calibration imports are versioned and never overwrite historical attempt scoring.

### Required tests

- Event idempotency, BKT numeric boundaries, prerequisite blocking, no-content fallback, repetition limits,
  path refresh, calibrated/uncalibrated fallback, information selection, and immutable scoring versions.

## 5. Semantic tutor and safety

### Runtime contract

- Introduce a retrieval port so Tutor does not depend on SQL text matching.
- Index only approved content and retain content type, ID, revision, level, and access scope metadata.
- Use hybrid retrieval: lexical candidates plus vector similarity, then deterministic filters and reranking.
- Add dedicated modes for Q&A, role play, sentence correction, and writing feedback with strict output schemas.
- Summarize long conversations without replacing the immutable message history.
- Detect prompt-injection patterns in retrieved content and learner messages; delimit untrusted content.
- Refuse unsupported claims when grounding is required and return citations tied to stored revisions.
- Add input/output safety categories and a review path without storing secrets or raw provider errors.

### Required tests

- Retrieval authorization, approved-only filtering, stale revision handling, injection payloads, citation integrity,
  insufficient-context refusal, mode schemas, summary rollover, and conversation ownership.

## 6. Speaking coach

### Runtime contract

- Provider name comes from the AI service response and is persisted dynamically.
- Aggregate errors by normalized word/phoneme/error type without retaining audio beyond consented retention.
- Generate next-practice recommendations linked to approved speaking/shadowing content.
- Expose weekly/monthly progress using deterministic aggregates.
- Support a multi-turn practice session while assessing each learner turn independently.
- Preserve speech-engine pronunciation scores; language-model feedback cannot alter them.

### Required tests

- Provider provenance, repeated-error aggregation, recommendation eligibility, retention deletion,
  multi-turn ownership/order, missing audio, malformed provider data, and progress date windows.

## 7. Realtime operations

### Runtime contract

- Expose authenticated SSE events for jobs owned by the current user with heartbeat and reconnect cursor.
- Persist an outbox event in the same transaction as terminal job state; delivery is at-least-once.
- Optional in-app notifications link to the domain result and contain no learner text.
- Metrics break down capability/provider/model/outcome and include retry count, queue delay, execution latency,
  token usage, estimated cost, and unresolved feedback.
- Readiness checks database, active provider configuration, internal service authentication, and object storage
  only for enabled capabilities.
- Define alert thresholds and runbooks for queue age, provider errors, schema failures, and cost anomalies.

### Required tests

- SSE ownership, reconnect, duplicate delivery, cancellation, terminal outbox atomicity, notification privacy,
  readiness combinations, and metric label cardinality.

## 8. Evaluation and production acceptance

### Runtime contract

- Version golden cases for Tutor, Placement reports, Learning Path explanations, Speaking language feedback,
  Writing assessment, and content generation.
- Separate deterministic contract checks from scored human-quality checks.
- Run each stochastic assessment multiple times and report schema success, evidence fidelity, score variance,
  agreement with human gold, unsafe-response rate, latency, tokens, and cost.
- Compare candidate prompt/model policies against the active baseline before activation.
- Block production activation when hard safety/schema thresholds fail; require a human decision for quality gates.
- Store evaluation metadata and hashes, not production learner content.

### Production gates that code cannot self-complete

- Rotate any credential that was previously stored in plaintext.
- Human-review generated learning content before publication.
- Calibrate IRT/BKT with real, consented learner observations.
- Run provider evaluations against human gold in the authorized environment.
- Upload and smoke-test media against the production object-storage destination.
- Configure production secrets, network policy, alert delivery, backup, retention, and incident ownership.
