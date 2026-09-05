# AI evaluation and production acceptance

## Scope

Create a versioned suite for each capability: Tutor, Placement report, Learning Path explanation, Speaking language
feedback, Writing assessment, and Content generation. Cases must be synthetic or explicitly approved test fixtures;
never copy production learner text into an evaluation suite. The database stores provider-output hashes, aggregate
metrics, and violations, not provider output bodies.

## Case contract

Each case supplies prompt variables and an `expectedContract` object. Supported deterministic fields are:

- `requiredFields`: JSON fields that must exist in every output.
- `requiredEvidence`: synthetic evidence phrases that must be preserved or cited.
- `forbiddenTerms`: secrets, unsafe phrases, answer-key material, or known injection markers that must not appear.
- `expectedContains`: quality concepts used for a deterministic coverage score when no numeric score is returned.
- `scoreField`: optional numeric output field used for variance and human agreement.
- `humanGoldScore`: optional reviewer gold score on a 0–100 scale.

Use at least three attempts per stochastic case. Keep temperature, model, prompt version, provider, output-token limit,
and prices fixed within a candidate. A candidate may reference an accepted baseline run; its summary then records
schema, evidence, safety, latency, and cost deltas.

## Gate sequence

1. `POST /api/admin/ai/evaluations/suites` creates the immutable, hashed suite and cases.
2. `POST /api/admin/ai/evaluations/runs` creates a candidate and queues a run.
3. The evaluation worker runs every case repeatedly and records schema success, evidence fidelity, unsafe response,
   score variance, human-gold agreement, P95 latency, tokens, and estimated cost.
4. Schema, evidence, and unsafe-rate thresholds are hard gates. Failure automatically rejects the run.
5. A hard-gate-passing run enters `AWAITING_HUMAN`. Reviewers inspect authorized provider artifacts outside the
   production learner-data path and record an accept/reject decision with a reason.
6. Prompt activation requires the accepted run ID as `evaluationRunId`. Enabling a model policy also requires a run
   whose capability/provider/model exactly match. Disabled policies do not require a run.

## Production acceptance checklist

- Rotate any credential ever stored in plaintext and confirm no secret exists in Git history or logs.
- Run every suite against the production-authorized provider and complete human scoring.
- Human-review generated learning content before publication.
- Calibrate IRT/BKT parameters using real, consented observations; import them as a new immutable scoring version.
- Upload and smoke-test every required media asset against production object storage.
- Configure secret management, private service networking, alert delivery, backups, retention, and named incident
  ownership.
- Verify database migrations on a production-like backup and test rollback by deploying the previous application
  version without reverting committed migrations.

These environment and human-review gates cannot be truthfully completed by source code alone. Production activation
must remain blocked until their evidence is attached to the release record.
