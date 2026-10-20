# Latency improvement plan — 20 October 2026

## Baseline and scope

The supplied testing log records 253.382 seconds of Spring startup and 264.993
seconds of process uptime at startup completion. Flyway validates in 1.574 seconds
and performs no migration. Requests after startup sometimes take 3–9 seconds;
neither a health response nor a successful deployment proves ordinary API latency.

Changes cover BE packaging/startup diagnostics, the account bootstrap read path,
and browser recovery. Keep authorization, schema validation, Flyway, admission
limits, and truthful readiness. Do not purchase hosting, copy testing data, add
keep-alive automation, or consume learner AI quota for performance tests.

## Execution order

1. Record controlled baseline: startup-to-readiness and representative authenticated
   reads on the isolated local PostgreSQL. Use the same CPU/heap, data and flags
   for comparisons. Separately retain the supplied Render log as a hosting baseline.
2. Extract the Spring executable archive at image build time. Benchmark the normal
   JVM against startup-oriented compilation before choosing a release default.
   Do not move uninitialized repositories behind a prematurely healthy endpoint.
3. Buffer startup steps and expose startup/Hikari/HTTP diagnostics to ADMIN only.
   These diagnose bean initialization, connection acquisition and request latency
   without logging prompts, tokens, SQL parameters or personal profile contents.
4. Combine account and onboarding reads in one owner-scoped bootstrap endpoint.
   Update BFF with older-backend fallback and preserve nullable onboarding behavior.
   Verify identical profile/onboarding values and no cross-account caching.
5. Keep the browser's bounded recovery active long enough for the measured cold
   start, with capped backoff, hidden-tab/offline handling and one recovery event.
   This improves recovery, not the server's actual startup duration.
6. Run focused and full checks, production builds, and a controlled before/after
   comparison. If promoted to testing, verify release SHA and first-use latency
   separately; report local measurements as local, never as a Render SLA.

## Acceptance and rollback

- A smaller startup time counts only if readiness and first authenticated reads
  remain correct. Preserve Flyway/schema validation and worker scope isolation.
- Bootstrap must reduce CurrentUser's two sequential REST calls to one on the new
  BE, and retain compatibility with a backend that lacks the endpoint.
- Guest, learner and staff cannot access startup/metrics diagnostics. Admin can.
- Startup-oriented JVM options must be reversible through deployment configuration;
  reject a material steady-state regression in the controlled comparison.
- Browser recovery must still work after two minutes and stop at its documented
  bound; it must not replay mutations or issue paid AI requests.
- Render Free may still sleep on idle. Eliminating that hosting delay requires a
  separate, user-approved infrastructure choice; do not promise it away in code.

References: [Spring Boot efficient packaging](https://docs.spring.io/spring-boot/3.5/reference/packaging/efficient.html),
[startup instrumentation](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html),
[Render idle behavior](https://render.com/docs/free#spinning-down-on-idle).

## Local results

Three runs per variant, one pinned CPU, ActiveProcessorCount=1, 256MB heap,
isolated local PostgreSQL, AI disabled:

- Original executable jar: median readiness 29,875ms, CPU 28.38s.
- Extracted original jar: median readiness 27,109ms, CPU 25.94s.
- Extracted original jar with tier-1 compilation: median readiness 11,843ms.
- Final jar with bootstrap/diagnostics and tier-1 compilation: median readiness
  12,407ms, CPU 11.77s — about 58% lower than the original controlled baseline.

These are local constrained-process measurements, not a prediction of Render's
wake time. The compilation option is enabled by CD for testing only; other
branches retain their normal JVM compilation mode. Render can override it through
`JAVA_TOOL_OPTIONS`; removing `-XX:TieredStopAtLevel=1` restores normal tiering.
Extraction does not require a database or learner content during image build.

The database regression test verifies snapshot parity, no profile insertion on
GET, account isolation, at most three SQL statements for a learner bootstrap and
one for a Staff/Admin bootstrap. The old two-call read takes more statements.
No global profile cache or larger connection pool is introduced.

The same original jar was also exercised with 100 measured authenticated local
profile reads per JVM mode after 20 warmup requests: normal tiering median/p95
16/47ms; tier-1 median/p95 15/16ms. This small CPU-constrained read test found no
regression for that endpoint; it does not establish throughput for all workloads.

Before changing testing, a small three-sample warm read measured median CurrentUser
GraphQL latency of 1,797ms and the equivalent sequential REST pair at 844ms.
Retain sample count, release SHA and timing artifacts when comparing after deploy;
network/capacity variation remains relevant. No AI inference is used by this test.

## Release checks

- Backend clean verify: 729 tests, no failures or errors; three opt-in pipeline
  batch tests skipped. No schema migration is added by this change.
- BFF: 199 tests, type check, lint, production build, and stable GraphQL codegen.
- Web: 315 tests, production build, lint, and repository formatting check. A final
  focused recovery run also passes with the released 10-second backoff cap.
- Local browser/API acceptance: 27 checks pass across Learner, Staff and Admin,
  including one bootstrap backend call, account parity, diagnostics authorization,
  empty assessment filters, mobile overflow, and absence of browser page errors.
- Testing promotion must verify the deployed SHA and repeat warm account timings.
  A public numeric startup timing enables a direct Render comparison without
  exposing detailed bean or pool diagnostics.
