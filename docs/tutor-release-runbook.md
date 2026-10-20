# Tutor upgrade release runbook

## Candidate and boundaries

This change adds migrations V057–V068. Flyway remains the schema owner. Do not
edit applied migrations, run the local acceptance suite against Supabase, or let
a local worker consume the testing queue. Use a dedicated `englow_test_*` database
and a distinct `AI_QUEUE_SCOPE` for local acceptance.

The Tutor owns delivery receipts, answer versions, curated resources, report
review, private short recordings and chat-data deletion. Quiz still owns its
answer keys, grading and practice attempts. Only the caller's SCORED quiz question
can be supplied to Tutor. Completed Writing/Speaking context comes through the
assessment owner's API; scored exam context includes only the selected question
and its bounded related passage/instructions. Media URLs and hidden criterion
scores are omitted. Chatting alone does not award practice XP.

## Configuration

- `AI_ENABLED` controls AI admission; `AI_WORKER_ENABLED` controls worker execution
  and defaults to **false**. A missing worker setting must not start a local consumer.
- `AI_QUEUE_SCOPE` defaults to `local` and must identify the deployment. Existing
  jobs migrate to `testing`. Before rollout, explicitly configure the testing BE
  with `AI_QUEUE_SCOPE=testing` and `AI_WORKER_ENABLED=true`; otherwise deployed
  jobs intentionally will not be consumed. Give other environments distinct scopes.
  CD now supplies explicit branch-scoped defaults in release images: a `testing`
  push builds with `AI_QUEUE_SCOPE=testing` and `AI_WORKER_ENABLED=true`. Ordinary
  local Docker builds retain `local`/`false`. Explicit Render environment values
  override image defaults, so verify any existing settings before rollout.
- `AI_MAX_OUTSTANDING_JOBS` defaults to 200 across the scope; excess work is refused
  before acceptance, with the browser draft retained.
- `AI_DAILY_REQUEST_LIMIT` is shared by Tutor, transcription, Writing and Speaking.
- `AI_SERVICE_BASE_URL` and `AI_SERVICE_INTERNAL_API_KEY` must agree with the adapter.
  A public health response does not prove the internal key or provider key works.
- `TUTOR_STREAMING_ENABLED=false` and `TUTOR_VOICE_ENABLED=false` are release gates.
  Enable each only after its deployed proxy/provider smoke test passes.
- `TUTOR_MEMORY_ENABLED=false` is a separate release gate. A learner explicitly
  requests each summary, consuming one ordinary shared AI allowance for bounded
  extracts of at most 20 completed messages. Further batches require another
  action. Delivery is asynchronous and recoverable through the ordinary receipt.
  Notes have a cursor, epoch and prompt version; they are not a verified profile.
- `TUTOR_AUDIO_BUCKET` defaults to the existing private `speaking` bucket.
  Keep `TUTOR_MAINTENANCE_ENABLED=true` even when the provider is disabled.
- `AI_SERVICE_LLM_REASONING_EFFORT=medium` preserves the adapter's generic default.
  Tutor explicitly requests low effort and a 2,048-token completion budget on
  supported Groq GPT-OSS models. Other compatible providers retain their normal
  request shape. No reasoning trace is returned as a learner answer.
- BFF's Vercel function and web SSE route allow 60 seconds. Streams reconnect
  before that limit and can fall back to lightweight reply polling.

Admin's Tutor page exposes a scope-specific admission switch and four mode
switches. Changes require a reason, optimistic version and audit entry. Pausing
refuses new questions and transcriptions but preserves receipts/history and lets
accepted work finish. Staff cannot change policy. Deployment AI flags retain
precedence; the web switch cannot override a disabled deployment. Model, quota,
streaming and voice capabilities remain deployment-controlled and read-only here.

Groq documents the model-specific effort values, completion budget and rate-limit
headers in its [API reference](https://console.groq.com/docs/api-reference),
[reasoning guide](https://console.groq.com/docs/reasoning), and
[rate-limit guide](https://console.groq.com/docs/rate-limits). Output-budget
exhaustion is a distinct terminal error, not a completed truncated answer.

## Deployment order

1. Back up the testing database and apply the additive migrations through Flyway.
2. Deploy the adapter first: it accepts old requests and the new structured turns,
   reasoning option, streaming endpoint and nullable usage.
3. Set the explicit deployment worker/scope settings above, then deploy BE with
   optional capabilities off. Verify the release and scope. Stop
   incompatible old workers before accepting new job types.
4. Deploy BFF/schema, then web. Verify generated OpenAPI/GraphQL contracts match.
5. Use synthetic prompts with the existing three test roles. Verify durable
   receipt recovery, account isolation, report assignment/correction, published
   resource withdrawal, Quiz completion checks and chat deletion.
6. Enable streaming and voice separately after testing Vercel/Render buffering,
   time limits, HTTPS, private storage and microphone recovery on the actual URLs.
7. Enable memory only after its bounded batches, explicit allowance cost, stream/
   receipt recovery, correction invalidation and teacher quality review pass.
   Memory uses `TUTOR_REPLY` with a purpose marker; drain accepted summary work
   through a worker that understands it before rolling back.

## Failure handling

- `AI_SERVICE_HTTP_401`: compare the BE/adapter internal keys. It is a configuration
  refusal; do not repeatedly replay private learner content to diagnose it.
- `AI_PROVIDER_HTTP_401`: check the adapter's provider credential/configuration.
- `AI_PROVIDER_HTTP_429`: retain `Retry-After`; inspect provider limits and the
  measured admission rate. A worker restart is not extra provider capacity.
- `AI_OUTPUT_BUDGET_EXCEEDED`: no complete answer was produced. Shorten/split the
  question or review the model budget; do not publish partial text as a final answer.
- `TUTOR_SERVICE_UNREACHABLE` / stream interruption: check adapter readiness and
  trace IDs. Accepted questions remain durable; reconnecting never enqueues a job.
- Pending jobs: check scope, worker flag, oldest pending age, lease/heartbeat and
  terminal error counts. The reconciler repairs interrupted terminal notifications.
- Storage deletion failure: the DELETED row remains a cleanup intent. Restore
  storage access and leave maintenance on. Never claim file deletion succeeded
  merely because the chat row disappeared.

## Data and review

Archive only hides a conversation. Tutor deletion removes conversations,
messages, answer snapshots, reports and queue content, while retaining allowance
accounting and short-lived delivery IDs. Recording deletion is retried durably;
recordings otherwise expire after 24 hours. JSON export contains conversations,
versions, report status/learner notes and transcript metadata, not provider keys
or reviewer-private notes. Export is paged and reflects the data as read; it is
not a database backup or an export of the user's separate Quiz records.

Application deletion does not attest to provider-side logs or backup retention;
verify those deployment policies separately. Published learning materials and
the account itself are outside a Tutor-chat deletion request.

Memory is included in the owner-scoped paged export even when new summary work
is disabled. "Stop using these notes" clears derived context; the summary message
still belongs to the chat history. Full Tutor-data deletion removes the memory
row and scrubs its corresponding queue payloads. An account switch aborts an
in-progress export instead of downloading mixed-account data.

Original AI answers remain unchanged when a reviewer corrects them. Approved
corrections follow the corresponding historical answer content, remain visible
when it is selected again, and accompany that answer in subsequent context.

## Acceptance evidence and outstanding gates

Local acceptance uses a real isolated PostgreSQL and synthetic content only.
Provider evaluation fixtures live in `ai_service/evaluations/tutor_cases.json`.
Run `python evaluations/run_tutor.py` to inspect the manifest; `--live` additionally
requires the internal key and an output path outside version control.

HTTP/content success is a technical check, not a teaching-quality score. The
120-case diagnostic run still needs qualified rubric review; the project owner
explicitly retained teacher approval before release on 20 October 2026. Two supplied-source
cases omitted the expected citation marker. Uncited answers are labelled as such,
and the server rejects citations outside its supplied reference list.

The 15-minute local load run used 1/5/10 synthetic JWT identities, one Tutor slot,
a separate database, real provider calls and loopback HTTP. Its artifact records
the exact jar hash. Development builds on the same machine can affect latency;
these measurements are not a Vercel/Render SLA or a cold-start measurement.

Semantic retrieval is not enabled without a verified embedding/vector setup.
Six additional synthetic memory scenarios cover reviewer corrections, private
identity, unmeasured level, injection/fake approval and missing evidence. Their
technical HTTP results do not replace teacher approval. Summary jobs require an
explicit learner action per bounded batch; there is no unbounded automatic spend.
Runtime model/quota changes are not exposed as unrestricted browser
inputs; model/quota/advanced-feature controls remain deployment environment settings.

## Rollback

Turn off optional admission flags first. Drain or close accepted generations
through a compatible worker; do not reset jobs or delete learner evidence.
Do not deploy an image that cannot deserialize a live job type. Prefer a
compatible forward fix. Keep additive schema changes and historical data intact.
