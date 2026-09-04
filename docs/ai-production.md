# AI production architecture and runbook

## Service boundary

The project uses two runtime services with a deliberately narrow internal contract:

- Spring Boot owns every public API, JWT authorization, deterministic business rule,
  PostgreSQL transaction, durable job, quota and audit record.
- `ai_service/` is a stateless FastAPI inference adapter. It owns external LLM and
  speech provider credentials, provider-specific payloads and response normalization.
- Spring calls FastAPI with `X-Internal-API-Key` over a private network. No frontend
  or mobile client calls FastAPI directly.

This split does not change public endpoints or database ownership. It adds one
internal HTTP hop and an independently deployable/scalable failure boundary.

## Scope

The AI platform implements five isolated capabilities on top of one durable PostgreSQL job queue:

| Capability | Deterministic responsibility | AI responsibility |
|---|---|---|
| Tutor | ownership, history, approved-content grounding and citations | grounded explanation |
| Placement | exam integrity, grading, CEFR band and learner-profile update | learner-friendly report only |
| Learning path | prerequisites, mastery/BKT updates and ordering | explanation of the computed path |
| Speaking | private audio lifecycle and Azure pronunciation scores | grammar and vocabulary feedback only |
| Content generation | draft/review/publish state machine | staff-editable draft generation |

AI output is never trusted for authorization, grading, CEFR placement or direct publication.

## Main API groups

- `/api/ai/jobs/{jobId}`: poll or cancel an owned asynchronous job.
- `/api/ai/tutor/conversations`: create, list, archive and message the grounded tutor.
- `/api/placement`: start, answer, submit and read deterministic placement results.
- `/api/learning-paths`: generate, read, progress and update preferences.
- `/api/speaking/sessions`: create an upload session, submit audio, read history/result and delete a recording.
- `/api/ai/feedback`: report an AI response.
- `/api/staff/ai/content`: generate, edit and submit a draft for review (`STAFF` or higher).
- `/api/reviewer/ai/content`: approve/reject pending content (`CONTENT_REVIEWER` or `ADMIN`).
- `/api/admin/ai`: prompt versions, model policies, reports, reviewed content and operational metrics (`ADMIN`).

Swagger exposes the exact request/response schemas at `/swagger-ui/index.html`.

## Required production configuration

Copy `.env.example` and replace all local defaults. At minimum:

- `SUPABASE_ISSUER_URI`, `SUPABASE_JWKS_URI`, `SUPABASE_JWT_AUDIENCE`.
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` with TLS enabled.
- `AI_ENABLED=true`, `AI_SERVICE_BASE_URL`, `AI_SERVICE_INTERNAL_API_KEY`,
  `AI_DEFAULT_MODEL` for Spring Boot.
- `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET` for a private bucket.
- In FastAPI: `AI_SERVICE_LLM_ENABLED=true`, `AI_SERVICE_LLM_BASE_URL`,
  `AI_SERVICE_LLM_API_KEY`.
- For speaking: set `SPEECH_ENABLED=true` in Spring and
  `AI_SERVICE_SPEECH_ENABLED=true`, `AI_SERVICE_AZURE_SPEECH_BASE_URL`,
  `AI_SERVICE_AZURE_SPEECH_API_KEY` for FastAPI.
- Restrictive `CORS_ALLOWED_ORIGINS`.

Set cost rates for every active model through `PUT /api/admin/ai/model-policies/{capability}`. Rates are expressed per one million tokens and feed both per-job and aggregate cost metrics.

## Deployment gates

1. Run `pytest` in `ai_service/` and `mvn test` at repository root. `FlywayMigrationTest` applies every migration to PostgreSQL when Docker is available and skips only on developer machines without Docker.
2. Run `mvn formatter:validate`, `mvn package`, and build both container images.
3. Deploy FastAPI on the private application network. Verify `/health/live` and `/health/ready`; expose neither `/internal/*` nor port 8000 publicly.
4. Deploy Spring with AI and speech disabled first and verify `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness`.
5. Configure prompt/model policies with provider `ai-service`, canary one capability, then enable the remaining capabilities.
6. Alert on `englow3.ai.requests{outcome="failure"}`, latency, queue depth, recent failures, FastAPI readiness and provider quota errors.
7. Verify the object-storage lifecycle is at least as strict as `SPEECH_AUDIO_RETENTION`; the application also deletes expired recordings.

## Operational behavior

- Jobs use row locking with `SKIP LOCKED`, idempotency keys, bounded exponential retry and stale-lock recovery.
- A reconciliation worker repairs business records after terminal/cancelled jobs, including jobs recovered after a worker crash.
- Prompt text and prompt version are frozen into each job so an admin activation cannot change in-flight work.
- Provider error bodies, credentials and raw exceptions are not returned to clients.
- User quotas are reserved atomically per UTC day. Token usage and configured estimated cost are stored for audit.
- Speaking uploads accept only signed WAV PCM 16 kHz or OGG Opus requests, enforce size/content type and verify file signatures before processing.
- Speech recordings require explicit consent and are private, user-scoped and automatically retained/deleted.

## Rollback

Disable a capability through its model policy first; queued data remains auditable. Roll back application code without reverting Flyway migrations. Prompt versions are append-only: activate the previous version rather than editing a deployed version. Do not delete AI jobs or audit rows during an incident.
