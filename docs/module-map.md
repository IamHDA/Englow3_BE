# Module map

Rules that hold at every depth level:
- one module owns writes to a table
- persistence types stay inside their module
- cross-module references are IDs
- transaction boundary at the use case
- schema changes through migrations only
- shared/ holds technical types only, never domain
- query code is read-only; writes go through the owning module

Architecture: three layers - controller -> service -> repository, modules
first and layers inside them. Business rules live in entities where an
invalid business state is possible; everything else is plain CRUD.
Decide rule placement per entity, not per module.

There is no `admin` module. Admin and learner features for one capability live
in the same module and differ only by controller (`/api/admin/**` with
`@PreAuthorize`).

## shared + config (technical packages, not business modules)

- **Subdomain:** n/a - infrastructure, not a business capability
- **Owns writes to:** none
- **Reads from:** none
- **Contains:**
  - `config`: `SecurityConfig` (SecurityFilterChain, Supabase JWT via `jwk-set-uri` + `jws-algorithms: ES256`, method security), `StorageConfig` (S3 client/presigner beans + nested `S3Properties`). Redis needs no config class - Spring Boot autoconfigures the connection factory and `StringRedisTemplate` from `spring.data.redis.*`.
  - `shared/security`: `CurrentUser` - exposes only what the Supabase JWT carries (`authProviderId` = `sub`, `email`). Internal user id and business role live in the `users` table and are resolved by the `user` module, not here.
  - `shared/error`: `DomainException` (code + `HttpStatus`), typed bases `NotFoundException` / `ConflictException` / `BadRequestException` / `ForbiddenException`, `ApiErrorResponse`, `GlobalExceptionHandler` (also maps `OptimisticLockingFailureException` -> 409 `CONCURRENT_UPDATE`).
  - `shared/storage`: `ObjectStorageClient` - generic `upload(bucket, key, stream)` / `presignGet(bucket, key, ttl)` / `delete`, no knowledge of what a key means.
- **Explicitly does NOT contain:**
  - business error codes (e.g. `ATTEMPT_ALREADY_SUBMITTED`) - live in the owning module, exception extends `DomainException`
  - `@PreAuthorize` / "who can do what" rules - declared per module at the controller/service that owns the action
  - business enums (e.g. `TargetSkill`) - they belong to the owning module
  - Redis lock key naming, TTL, and lock semantics - owned by the module that needs the lock
  - S3 object key/path conventions and bucket choice per file type - owned by the module that owns the file: `user` (avatar/banner), `exam` (question audio/image, speaking recordings)
- **Why:** security/error/storage config are pure infrastructure - no business vocabulary, no business reason to change, and adding a field to them forces no module to change. Auth is delegated to Supabase (JWT issuance, no self-managed sessions), so this layer only verifies tokens.
- **Revisit if:** any business-specific rule (lock semantics, key naming, authorization logic) starts leaking in - that is domain logic misplaced, move it into the owning module. Also revisit if the project stops relying on Supabase for auth.

## user

Covers identity, learner profile, and the **onboarding flow**. Onboarding is a
set of use cases inside this module, not a module of its own.

- **Subdomain:** not settled yet. The onboarding flow is where design attention goes (rules modelled in entities); the rest of the profile stays plain CRUD.
- **Owns writes to:** `users`, `learner_profiles`, `user_learning_purposes`, `user_target_skills`, `learning_purposes`
- **Reads from:** nothing directly. Attempt data arrives through the `exam` module's service as records.
- **Entities with rules:**
  - `User` - `completeOnboarding(...)` refuses when the learner has no learning purpose or no `current_level`. Receives plain values only (`boolean hasPurpose`, `String currentLevel`, `Instant now`).
  - `LearnerProfile` - `placement_attempt_id` can be assigned only once and only for the certificate branch; `current_level` is written from the attempt's `assessed_level`. Carries `@Version` (written by both the request thread and the attempt-scored listener); conflict surfaces as 409.
  - Plain CRUD: `LearningPurpose`, and the join rows in `user_learning_purposes` / `user_target_skills`.
- **Talks to `exam` via:**
  - module API - starts the placement attempt (`exam_type=PLACEMENT`) and stores the returned id in `placement_attempt_id`; reads a per-skill result record (`skillType`, `correctCount`, `questionCount`) to build target-skill recommendations
  - domain event - `exam` publishes "attempt scored"; this module listens and, when the id matches `placement_attempt_id`, writes `assessed_level` into `current_level` and `converted_score` into `current_score`. A self-declared level leaves `current_score` empty.
  - ID only - `placement_attempt_id` is a `UUID` field, never a `@ManyToOne`
- **Read path:** repository projections. No `query/` package needed.
- **Files it owns:** avatar and banner. They are served as a **stable public URL** (`app.storage.public-base-url` + object key), not a presigned one - they are shown openly in the app anyway, and a signed URL only adds an expiry to manage mid-session. Presigning stays for genuinely private files (submissions, speaking recordings) owned by other modules. This needs the bucket to allow anonymous reads, and object keys that cannot be guessed.
- **`GET /api/me`** returns identity plus the nested onboarding state, so the frontend boots from one call.
- **Onboarding flow** - `users.onboarding_step` records where the learner stands, as a named step (`OnboardingStep` enum in `user/entity/`, persisted as `@Enumerated(EnumType.STRING)`), not a number. The flow branches, so a number would mean different things to different learners, and inserting a step later would silently change what stored values mean.
  1. `LEARNING_PURPOSES` - pick learning purposes (at least one)
  2. `CERTIFICATE_TARGET` - a `CERTIFICATE` purpose makes `target_certificate_type` mandatory; other branches skip this step
  3. `CURRENT_LEVEL` - self-declared, or "don't know" -> certificate branch takes the placement test (exactly once); non-certificate branch is meant to take a quiz, which does not exist yet, so it returns `QUIZ_NOT_AVAILABLE` and the learner must self-declare
  4. `LEARNING_GOAL` - only reachable once `current_level` is known. Certificate branch sets `target_score` + `target_date`; other branches set `target_date` only (no score to aim at). **Skippable** - the learner can set it later in the profile.
  5. `TARGET_SKILLS` - pick skills to improve, with a "don't know" option; after a test, recommendations are derived from the per-skill result
  6. `COMPLETED` -> `is_onboarding_completed = true`
- **Why:** onboarding owns no table of its own; it only fills tables that this module must own anyway, because the same rows are edited outside onboarding (profile screen). A separate onboarding module would mean two writers on `learner_profiles` and `user_learning_purposes`. Role is not a boundary either, so admin catalog management is a controller here, not a module.
- **Revisit if:** onboarding grows tables of its own (persisted recommendations, complex wizard state, flow variants for A/B) - that is the signal to split it out.

### Schema changes for this module (written, not yet applied)

Migrations V001-V025 have run; never edit them. V026-V029 and the repeatable seed exist as files and apply on the next application start:

- **V026** - drop `target_skills`; `user_target_skills.target_skill_id` becomes `skill varchar(20)` fed by the `TargetSkill` enum in `user/entity/`, primary key `(user_id, skill)`. Trade-off accepted: a new skill needs a deploy and there is no admin UI for the list; in exchange one table and one CRUD layer disappear.
- **V027** - `learner_profiles.placement_attempt_id uuid references exam_attempts (id)`. One row per learner already, so one column means at most one placement attempt - no partial index, and the constraint sits in a table this module owns.
- **V028** - `users.onboarding_step` from `smallint` to `varchar(30)`, default `LEARNING_PURPOSES`, existing `0` rows mapped to the same value. Values: `LEARNING_PURPOSES`, `CERTIFICATE_TARGET`, `CURRENT_LEVEL`, `LEARNING_GOAL`, `TARGET_SKILLS`, `COMPLETED`.
- **V029** - unique constraint on `learning_purposes.purpose_code`, so the catalog can be seeded idempotently and `CERTIFICATE` resolves to exactly one row.
- **R__seed_learning_purposes.sql** - repeatable seed for the catalog (`CERTIFICATE`, `COMMUNICATION`, `WORK`, `STUDY_ABROAD`, `SCHOOL`). Only `CERTIFICATE` is load-bearing (it drives the branch); the rest is placeholder content, edit the file and it re-applies.

## exam (not built yet)

Recorded here only because the `user` module's design depends on it: **exam content and attempts are one module**, so `attempt_answers -> questions` is an internal join, not a cross-module read. It owns `exams`, `exam_sections`, `section_parts`, `question_sets`, `questions`, `*_answers` reference tables, `grading_criteria`, `score_conversions`, `exam_attempts`, `attempt_*`. Its own design conversation is still to come.

## Deliberately left open

- **Quiz** - tables not designed. It gets its own module when built (own tables, own admin CRUD); the `user` module calls into it. Not folded into `user`, not folded into `exam`.
- **Subdomain classification** - the product's competitive edge is not settled, so no module is formally marked core yet.
- Concrete values of the `TargetSkill` enum vs `questions.skill_type`, especially values that exist on only one side (e.g. Pronunciation). The `skill_type -> TargetSkill` mapping lives in the `user` module; the enum is never shared between modules.
- Unknown values in `user_target_skills.skill` once the FK is gone - decide between ignoring them on read or a cleanup migration, when a value is actually removed.
- Snapshotting `skill_type` into `attempt_answers` - not done. The recommendation is consumed immediately during onboarding, so later reclassification is harmless. Revisit when per-skill progress over time becomes a feature and old results must keep their meaning.
