# AI operations runbook

## Service-level signals

Alert on the following production conditions. Tune warning thresholds after two weeks of real traffic, but never
weaken a hard safety gate to clear an alert.

| Signal | Warning | Critical | First response |
| --- | ---: | ---: | --- |
| Oldest ready job | 60 seconds | 5 minutes | Check worker replicas, database locks, then provider readiness. |
| Provider request failure rate (15 min) | 5% | 20% | Group by capability/provider/model; disable only the affected policy if failures persist. |
| Schema/citation/evidence failures (15 min) | 3 | 10 | Freeze the candidate prompt/model and restore the last accepted policy. |
| P95 execution latency | 30 seconds | 90 seconds | Check provider latency, output-token limits, and worker saturation. |
| Daily estimated cost versus 7-day mean | +30% | +75% | Check request volume, retry counts, policy changes, and abusive users. |
| Unresolved unsafe/incorrect feedback | 10 | 50 | Assign reviewers; disable the capability when unsafe reports are credible. |

Core metrics are `englow3.ai.requests`, `englow3.ai.latency`, `englow3.ai.job.queue.delay`,
`englow3.ai.job.execution`, `englow3.ai.job.transitions`, and `englow3.ai.job.retry.count`. Labels are bounded to
capability, configured provider, configured model, and outcome. Never add user IDs, job IDs, prompts, or error text
as metric labels.

## Incident sequence

1. Confirm `/actuator/health` and the private FastAPI `/health/ready` result.
2. Inspect queue age and transitions by capability/provider/model. Do not inspect raw learner input unless an
   authorized support case requires it.
3. For a provider incident, disable the affected `ai_model_policies` row. Existing deterministic learning features
   remain available; queued work can resume after re-enabling.
4. For a bad prompt/model release, deactivate it and activate the last evaluation-approved version. Preserve job,
   evaluation, and outbox history for the audit.
5. For worker loss, allow stale-lock recovery to schedule bounded retries. Do not manually duplicate jobs; reuse the
   original idempotency key.
6. Verify domain reconciliation after terminal failures and verify the SSE event cursor can replay the terminal event.

## SSE and notification contract

`GET /api/ai/jobs/events` is authenticated and returns only events owned by the current user. Clients persist the
numeric SSE event ID and reconnect with `Last-Event-ID` or `?after=`. Delivery is at-least-once, so clients must
deduplicate by event ID. A `RESYNC_REQUIRED` event means the client should reload its job list. Heartbeats contain no
business data.

Outbox payloads and in-app notifications contain only job/capability/status and domain target identifiers. They never
contain learner text, transcripts, prompt contents, provider response bodies, credentials, or answer keys.

## Retention and recovery

Operational events and notifications are retained for 90 days. Domain results and immutable evaluation records use
their own retention rules. Speaking audio deletion is independent from assessment retention. Database backup restore
must restore jobs and outbox events together so terminal state and client-visible state cannot diverge.
