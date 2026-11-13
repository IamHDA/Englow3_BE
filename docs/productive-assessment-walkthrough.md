# Writing and free-response Speaking practice

This module provides practice estimates, not certified IELTS results. Read-aloud
pronunciation remains a separate feature at `/study/pronunciation`. These tasks
are independent practice activities; the objective exam module is unchanged.

## Roles and routes

- **Staff:** `/admin/assessments`. Create a Writing Task 1/2 or Speaking Part 1/2/3
  task, add instructions, scoring notes and an optional reference answer, then
  send it for review. Staff can edit their own drafts/rejected tasks and grade
  submissions for tasks they authored.
- **Admin:** the same page, with access to all tasks. Preview and approve a pending
  task or return it with a reason. Published tasks can be archived. Admin can
  review submissions and correct completed grades; each human review stores an
  audit record with the previous report, replacement report, reviewer and note.
- **Learner:** `/study/writing` and `/study/speaking`. Only published tasks appear.
  Writing starts a draft with autosave, version conflict protection and a local
  recovery choice. Speaking records up to five minutes, supports replay/re-record,
  converts to mono PCM WAV at 16 kHz and uploads through a signed URL. The user
  must explicitly submit. History links back to saved drafts, pending submissions
  and completed reports.

## Results and recovery

Each report contains exactly four criterion scores from 0 to 9 in 0.5 steps,
evidence-based feedback, summary, strengths and next steps. The backend computes
the overall mean rounded to 0.5. Sample answers are hidden until completion.
Writing uses task response/achievement, coherence, vocabulary and grammar.
Speaking uses fluency/coherence, vocabulary, grammar and pronunciation.

Submission snapshots the task and seals the audio into a new private object;
an old upload URL cannot replace the evidence being graded. Repeated starts with
the same client request key and repeated submissions are idempotent. Revision
checks prevent a stale AI job from overwriting newer results.

Automatic grading disabled: submission moves directly to `NEEDS_REVIEW` and a
teacher supplies the four scores and feedback. Automatic grading enabled:
submission moves to `QUEUED`; provider errors receive bounded queue retries.
A terminal `FAILED` result offers explicit AI retry or human review. Retry uses
the same submitted evidence and a new grading revision. AI requests share the
existing daily allowance. Provider errors never create a fabricated score.

## Configuration

Apply migration V049. Start the BFF and web app alongside the backend. For audio,
configure S3 credentials/endpoint and a private `STORAGE_SPEAKING_BUCKET` (default
`speaking`). Local Docker setup: `docker compose up -d minio minio-init`.
The endpoint in signed URLs must be reachable from the learner's browser and
allow PUT/GET from the frontend origin. Do not make the speaking bucket public.

Default flags are deliberately false:

```dotenv
AI_ENABLED=false
ASSESSMENT_AUTOMATIC_WRITING=false
ASSESSMENT_AUTOMATIC_SPEAKING=false
AI_SPEECH_READ_TIMEOUT=240s
```

For automatic Writing, run `ai-service`, set a matching internal API key on both
services, configure/enable its LLM provider, then enable `AI_ENABLED` and
`ASSESSMENT_AUTOMATIC_WRITING`. For automatic Speaking, additionally configure
Azure Speech credentials, enable `AI_SERVICE_SPEECH_ENABLED` and
`ASSESSMENT_AUTOMATIC_SPEAKING`. Never expose provider keys to the web app.

The adapter splits long unscripted WAV recordings into quiet-boundary segments
below 30 seconds, combines ordered transcripts and duration-weighted acoustic
measures, and rejects incomplete segment results. These are limited acoustic
estimates, not continuous interview scoring. No percentage is mechanically
converted into a practice band. Actual provider accuracy/latency must be checked
with configured credentials before enabling automatic grading in production.

## Verification

Backend tests cover lifecycle, validation, ownership, idempotency, quota, report
normalization and provider failure handling. `AssessmentFlowIntegrationTest`
exercises both three-role Writing and Speaking workflows against PostgreSQL via Testcontainers;
it requires a functioning Docker engine and skips otherwise. Frontend tests cover
draft recovery, concurrent edits while saving and preserving manual-grade input
after a failed mutation. Python tests cover long-audio coverage, aggregation and
partial failures. No paid-provider acceptance test is implied by mocks.

Criteria references: [IELTS scoring](https://www.ielts.org/take-a-test/your-results/ielts-scoring-in-detail),
[Writing descriptors](https://cdn.ielts.org/Guides/ielts-writing-band-descriptors.pdf),
[Speaking descriptors](https://cdn.ielts.org/ielts-guides/ielts-speaking-band-descriptors.pdf).
Audio API constraints: [Azure pronunciation assessment](https://learn.microsoft.com/en-us/azure/cognitive-services/speech-service/how-to-pronunciation-assessment).
