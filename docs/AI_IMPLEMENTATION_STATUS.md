# AI implementation and production-readiness status

Last verified: 2026-08-24. This document describes the code present on the stacked feature branches listed
below. It does not claim that external provider, human-review, calibration, infrastructure, or production
deployment gates have been completed.

## Architecture boundary

```text
Authenticated client
       |
       v
Spring Boot API (system of record and orchestration)
  - ownership / RBAC / validation / deterministic scoring
  - PostgreSQL + pgvector / Flyway
  - durable jobs, outbox, SSE, evaluation and audit
       |
       | internal API key, bounded contract, timeout/retry policy
       v
FastAPI AI service (inference boundary)
  - LLM generation
  - speech assessment
  - 1024-dimensional embeddings
       |
       v
Configured model / speech / embedding providers
```

`data_pipeline/` is an offline authoring, validation, media and delivery toolchain. Runtime AI inference is
not stored there. Runtime provider code is in `ai_service/`; runtime domain orchestration is in
`src/main/java/com/englow3/ai/`.

## Feature status

| Capability | Production code completed | Main controls | Branch |
| --- | --- | --- | --- |
| AI service extraction | Yes | Internal API key, strict schemas, provider error classification, readiness | `feat/ai-service` |
| English data pipeline | Yes, tooling scope | Schemas, deterministic IDs, QA, attribution, guarded writes | `feat/english-data-pipeline-completion` |
| Writing assessment | Yes | Human-approved task, rubric completeness, evidence fidelity, backend weighted score | `feat/ai-writing-assessment` |
| AI content publication | Yes | Typed validation, immutable revisions, four-eyes review, atomic materialization, archive audit | `feat/ai-content-publication` |
| Personalized exams | Yes | Deterministic reviewed blueprint, quotas, candidate/media/leakage checks | `feat/ai-personalized-exams` |
| Adaptive learning | Yes | BKT audit trail, approved content references, idempotent events, skip/postpone/replace | `feat/ai-adaptive-learning` |
| Semantic tutor | Yes | Hybrid retrieval, stored revision citations, safety modes, injection filtering, fallback | `feat/ai-semantic-tutor` |
| Speaking coach | Yes | Speech provenance, immutable scores, error aggregation, practice recommendations, retention | `feat/ai-speaking-coach` |
| Realtime operations | Yes | Transactional outbox, owned SSE, private notifications, metrics, cleanup and readiness | `feat/ai-realtime-operations` |
| Evaluation gates | Yes | Versioned suites, repeated runs, hard gates, human acceptance, activation linkage | `feat/ai-evaluation` |
| Adaptive placement | Yes | Versioned immutable 3PL parameters, four-eyes activation, information selection, fixed fallback | `feat/ai-placement-calibration` |
| Embedding lifecycle | Yes | Approved-only queue, revision/hash guard, bounded retry, stale detection, admin re-index | `feat/ai-embedding-index` |

“Completed” means the repository contains the production-oriented workflow and automated verification. It
does not mean the external acceptance gates at the end of this document are complete.

## Runtime capabilities and contracts

### Foundation and operations

- `ai_jobs` is the durable request boundary. Capability policy resolves provider/model and attributes tokens,
  latency and estimated cost.
- State changes and outbox events are committed together. SSE and in-app notifications are ownership scoped;
  events contain identifiers/status, not learner text.
- Stale business states and worker locks are reconciled. Retries are bounded and provider response bodies are
  not returned to public clients.
- Prompt/model activation requires an accepted evaluation run. Hard schema/safety gates cannot be overridden by
  a human quality decision.

### Assessment and learning

- Writing assessment validates every rubric dimension and exact evidence spans. Java computes the weighted
  overall result; the model cannot set the final score.
- Speaking retains provider/engine provenance and pronunciation measurements. Language feedback cannot replace
  speech-engine scores. Audio has an explicit lifecycle and retention cleanup.
- Learning-path mastery updates are deterministic BKT events with before/after values and a deduplication key.
  Recommendations resolve to existing approved domain content.
- Adaptive placement uses the 3PL information function only for items in the active calibration that meet its
  minimum response policy. If the eligible pool is smaller than the requested minimum, it starts the selected
  published fixed placement exam instead.

### Content, retrieval and evaluation

- AI-generated content remains a draft until validation, a different reviewer approves it, and an administrator
  publishes it. Publication and domain-row materialization are atomic.
- Only `human_approved` content is queued for embedding. The worker snapshots content SHA-256 and revision, and
  refuses to write a vector if the live text or approval state changed.
- Tutor retrieval combines lexical and pgvector scores, excludes unapproved content, records exact revision/hash
  citations and degrades to lexical retrieval when the embedding service is unavailable.
- Evaluation suites store hashes and synthetic/golden metadata rather than production learner content. Candidate
  runs report contract success, variance, agreement, unsafe rate, latency, tokens and cost against a baseline.

## Important endpoints added

All public learner endpoints require authentication. `/api/admin/**` endpoints additionally require admin
authorization.

- Writing: `/api/writing/tasks`, `/api/writing/submissions`, `/api/writing/submissions/{id}`.
- Personalized exams: `/api/personalized-exams/**` plus admin review/publication endpoints.
- Learning paths: `/api/learning-paths/**` for generation, actions and completion events.
- Tutor: `/api/tutor/conversations/**` for mode-specific, grounded asynchronous replies.
- Speaking: `/api/speaking/sessions/**` for upload, turn assessment, recommendations and progress.
- Placement: `/api/placement/attempts/**` for fixed tests and `/api/placement/adaptive-attempts/**` for IRT.
- IRT calibration admin: `/api/admin/ai/placement/calibrations/**`.
- AI jobs/events: `/api/ai/jobs/**`, `/api/ai/jobs/events`, `/api/ai/notifications`.
- Evaluation admin: `/api/admin/ai/evaluations/**`.
- Embedding admin: `/api/admin/ai/embeddings` and `/api/admin/ai/embeddings/reindex`.
- FastAPI internal: `/internal/v1/llm/generate`, `/internal/v1/speech/assess`,
  `/internal/v1/embeddings`, `/health/live`, `/health/ready`.

Consult generated OpenAPI/Swagger for exact request and response schemas.

## Database evolution

| Migration | Purpose |
| --- | --- |
| `V100`-`V170` | AI job foundation, tutor, placement, paths, speaking, governance, operations and FastAPI routing |
| `V180` | Evidence-grounded writing assessment |
| `V190` | Typed publication, immutable revisions and domain publication records |
| `V200` | Deterministic personalized exam blueprints |
| `V210` | Auditable adaptive-learning events and content actions |
| `V220` | Tutor revision/citation/safety metadata |
| `V230` | Speaking multi-turn, provenance, progress and retention data |
| `V240` | Durable AI job event outbox and notifications |
| `V250` | Versioned AI evaluation suites/runs/gates |
| `V260` | Versioned IRT calibration and adaptive placement attempts/responses |
| `V270` | Approved-content embedding index lifecycle and shadowing vectors |

Flyway was verified from an empty PostgreSQL 17 + pgvector database through `V270`. Existing environments must
use Flyway normally; do not edit or replay an already applied versioned migration.

## Verification evidence

At the latest clean repository verification:

- Java: `mvn -q clean test` — 116 tests, 0 failures, 0 errors, 0 skipped.
- Flyway/Testcontainers: 49 migrations applied; 50 migrations validated including the repeatable seed; schema
  version `v270`.
- Java format: `mvn -q formatter:validate` passed.
- FastAPI: `python -m ruff check app tests`, `python -m ruff format --check app tests`, and
  `python -m pytest` passed — 34 tests; these checks are required by CI.
- Data pipeline: `python -m pytest -q` passed — 85 tests; this check is required by CI.
- Container verification: the FastAPI Compose image and the production Java Dockerfile both built successfully;
  `docker compose config --quiet` passed (with the expected warning when the local internal key is unset).

The test suite covers deterministic scoring/calculation, malformed provider responses, strict AI contracts,
idempotency helpers, retrieval/citation safety, prompt injection, provider provenance, evaluation gates,
IRT selection/hash stability, embedding dimensions and migration validity. Production smoke tests still require
real configured infrastructure.

## Branch order and integration

The feature branches are intentionally stacked because later migrations and contracts depend on earlier ones.
Merge or rebase them in this order:

1. `feat/ai-service`
2. `feat/english-data-pipeline-completion`
3. `feat/ai-writing-assessment`
4. `feat/ai-content-publication`
5. `feat/ai-personalized-exams`
6. `feat/ai-adaptive-learning`
7. `feat/ai-semantic-tutor`
8. `feat/ai-speaking-coach`
9. `feat/ai-realtime-operations`
10. `feat/ai-evaluation`
11. `feat/ai-placement-calibration`
12. `feat/ai-embedding-index`
13. `docs/ai-production-readiness`

Do not deploy a later migration without all earlier stacked branches. Use PRs into `dev`, validate the assembled
commit graph in the development environment, then promote `dev` to `main`.

## External production gates still required

These items cannot be truthfully completed by repository code alone:

1. Rotate any credential that has ever been stored or shared in plaintext, then configure secret management.
2. Configure production Supabase JWT issuer/JWKS/audience, PostgreSQL, object storage, internal AI key, provider
   credentials, allowed origins, network policy, backup and retention.
3. Human-review and publish an initial learning-content set; verify media rights/attribution and object URLs.
4. Import consented IRT calibration observations and have a different administrator activate the version. Until
   that succeeds, adaptive placement intentionally uses the reviewed fixed test.
5. Validate BKT parameters and recommendation behavior against consented learner observations.
6. Run each evaluation suite against authorized providers and human gold; accept only candidates that pass hard
   gates and quality review.
7. Run development smoke tests for JWT ownership/RBAC, SSE reconnect, uploads, provider timeout/retry, job
   reconciliation, archive/re-index, backup restore and incident alerts.
8. Configure dashboards and alert delivery using `docs/AI_OPERATIONS_RUNBOOK.md`; assign incident, content-review,
   data-protection and model-approval owners.

Until those gates are signed off, the accurate status is **code complete and locally verified, not yet approved
for production traffic**.
