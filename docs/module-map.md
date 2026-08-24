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
  - `config`: `SecurityConfig` (SecurityFilterChain, Supabase JWT via `jwk-set-uri` + `jws-algorithms: ES256`, method security), `StorageConfig` (S3 client/presigner beans + nested `S3Properties`).
  - `shared/security`: `CurrentUser` - exposes only what the Supabase JWT carries (`authProviderId` = `sub`, `email`). Internal user id and business role live in the `users` table and are resolved by the `user` module, not here.
  - `shared/error`: `DomainException` (code + `HttpStatus`), typed bases `NotFoundException` / `ConflictException` / `BadRequestException` / `ForbiddenException`, `ApiErrorResponse`, `GlobalExceptionHandler` (also maps `OptimisticLockingFailureException` -> 409 `CONCURRENT_UPDATE`).
  - `shared/storage`: `ObjectStorageClient` - generic `upload(bucket, key, stream)` / `presignGet(bucket, key, ttl)` / `delete`, no knowledge of what a key means.
- **Explicitly does NOT contain:**
  - business error codes (e.g. `ATTEMPT_ALREADY_SUBMITTED`) - live in the owning module, exception extends `DomainException`
  - `@PreAuthorize` / "who can do what" rules - declared per module at the controller/service that owns the action
  - business enums (e.g. `TargetSkill`) - they belong to the owning module
  - S3 object key/path conventions and bucket choice per file type - owned by the module that owns the file: `user` (avatar/banner), `exam` (question audio/image, speaking recordings)
- **Why:** security/error/storage config are pure infrastructure - no business vocabulary, no business reason to change, and adding a field to them forces no module to change. Auth is delegated to Supabase (JWT issuance, no self-managed sessions), so this layer only verifies tokens.
- **Revisit if:** any business-specific rule (lock semantics, key naming, authorization logic) starts leaking in - that is domain logic misplaced, move it into the owning module. Also revisit if the project stops relying on Supabase for auth.

## user

Covers identity, learner profile, and the **onboarding flow**. Onboarding is a
set of use cases inside this module, not a module of its own.

- **Subdomain:** **core** for the personalised path - placement, goal, and the skill recommendations derived from results are one of the two things the product competes on. Supporting for the rest: identity, avatar/banner, and profile editing are plain CRUD and should stay that way. That is why the onboarding flow carries entity rules and nothing else in the module does.
- **Owns writes to:** `users`, `learner_profiles`, `user_learning_purposes`, `user_target_skills`, `learning_purposes`
- **Reads from:** nothing directly. Attempt data arrives through the `exam` module's service as records.
- **Entities with rules:**
  - `User` - `completeOnboarding(CertificateLevel currentLevel, boolean certificatePurposeSelected, CertificateType certificateType)` refuses when the learner has no learning purpose, no `current_level`, or is on the certificate branch without a certificate. Receives plain values only - no repository, no service, no `Clock`.
  - `LearnerProfile` - `placement_attempt_id` can be assigned only once and only for the certificate branch; `current_level` is written from the attempt's `assessed_level`. **Needs `@Version`** - the request thread and the attempt-scored listener both write it - but no migration adds the column yet and no entity declares it. Until then the race is unguarded; conflict is meant to surface as 409 `CONCURRENT_UPDATE`, which `GlobalExceptionHandler` already maps.
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
  6. `COMPLETED` - the step is the only completion flag; there is no separate boolean column
- **Why:** onboarding owns no table of its own; it only fills tables that this module must own anyway, because the same rows are edited outside onboarding (profile screen). A separate onboarding module would mean two writers on `learner_profiles` and `user_learning_purposes`. Role is not a boundary either, so admin catalog management is a controller here, not a module.
- **Revisit if:** onboarding grows tables of its own (persisted recommendations, complex wizard state, flow variants for A/B) - that is the signal to split it out.

### Schema changes for this module (written, not yet applied)

No environment holds real data yet, so **migrations are rewritten in place** rather than chained - a correction folds back into the migration that created the column. That stops at the first real deployment; from then on Flyway checksums make an edited migration a startup failure, and every change needs a new version. The `target_skills` drop, the `onboarding_step varchar(30)` type, the `learning_purposes.purpose_code` unique constraint and the `target_certificate_type` check constraint were all folded back into V007 / V002 / V004 / V003 this way, so they have no migration of their own.

- **V026** - `learner_profiles.placement_attempt_id uuid unique references exam_attempts (id)`. One row per learner already, so one column means at most one placement attempt - no partial index, and the constraint sits in a table this module owns. The `unique` is what makes placement-once a database guarantee rather than an application check, so `exam` relies on it instead of adding a second mechanism.
- **V027** - `users.gender` and `users.birth_date`.
- **V028** - drop `users.is_onboarding_completed`; `onboarding_step = COMPLETED` is the single source of truth, a boolean beside it could disagree with it.
- **R__seed_learning_purposes.sql** - repeatable seed for the catalog (`CERTIFICATE`, `COMMUNICATION`, `WORK`, `STUDY_ABROAD`, `SCHOOL`). Only `CERTIFICATE` is load-bearing (it drives the branch); the rest is placeholder content, edit the file and it re-applies.

## exam

Covers exam content and **taking an exam**: starting an attempt, answering, submitting, scoring,
and reporting the per-skill breakdown the `user` module turns into recommendations. **Content and
attempts are one module**, so `attempt_answers -> questions` is an internal join, not a
cross-module read.

- **Subdomain:** **core**. Two things the product competes on land here: the sitting itself (time honoured, nothing lost) and the per-skill result that feeds the personalised path. Authoring is supporting, and deferred. AI feedback was explicitly *not* chosen as the differentiator, so it stays supporting when it arrives.
- **Owns writes to:** `exams`, `exam_sections`, `section_parts`, `question_sets`, `question_set_options`, `questions`, `question_options`, `question_matching_answers`, `question_accepted_answers`, `grading_criteria`, `score_conversions`, `exam_attempts`, `attempt_section_parts`, `attempt_section_results`, `attempt_answers`, `attempt_answer_options`, `attempt_answer_criterion_scores`, `ai_jobs`
- **Written by nothing yet:** every content and reference table above. There is no authoring endpoint in this phase - content is seeded by SQL - so `Exam`, `ExamSection`, `SectionPart`, `QuestionSet`, `Question` and the answer-key tables are **read-only entities**: no setters, no factories, no rules. Ownership is still recorded here because this module is where authoring will land.
- **Reads from:** nothing outside the module. `exam_attempts.user_id` and `exams.created_by_user_id` are plain `UUID`s.
- **Entities with rules:** `ExamAttempt` only.
  - `ExamAttempt` - refuses answering after submit or past `expires_at`; refuses a second submit; refuses scoring before submit or twice; refuses a `PLACEMENT` paper sat in custom mode or untimed; the selected part set is fixed at start. `@Getter` only, `protected` no-arg constructor, named factories `startFull` / `startCustom` that assign the `UUID` themselves, and no setter for `status`, `submittedAt`, `scoredAt`, `expiresAt` or any score field. `examType` arrives as a plain enum value so `startCustom` can refuse a placement paper without the entity ever touching `Exam`.
  - Near-plain: `AttemptAnswer`. "Can I still answer?" is `ExamAttempt`'s rule, and with synchronous grading an answer is never graded while answering is open. It keeps its own repository because answering is per-question and reloading the whole attempt each time is waste. Revisit if grading becomes async.
  - Plain: `AttemptSectionResult` (derived), `GradingCriterion` and `ScoreConversion` (seeded reference data), and all read-only content entities.
- **Two exam types, not three:** `exams.exam_type` is `PLACEMENT` or `MOCK`. They differ in configuration (duration, sections, whether a certificate is attached), in the step after scoring (placement writes back to `learner_profiles`; mock only records its own result), and in eligibility (placement once per learner; mock repeatable). They do **not** differ in scoring depth - both convert raw -> band/CEFR through `score_conversions` the same way.
- **Sitting mode belongs to the attempt, not the paper:** choosing which `section_parts` to do out of one full paper, with a freely chosen time limit, is a *way of sitting* a paper rather than a kind of paper. So `exam_attempts.attempt_mode` (`FULL` / `CUSTOM`) plus `attempt_section_parts` carries it, and `exams` stays untouched. `exam_attempts.max_raw_score` and `question_count` are already per-attempt columns, so a partial sitting needs nothing further - they are computed from the selected parts at start.
- **Grading lives here, not in a module of its own.** It writes `attempt_answers`, `attempt_section_results` and `exam_attempts`, all exam-owned; splitting it would put a second module in charge of writing this module's tables and would cut submit-and-auto-grade, one consistency boundary, in half. In this phase only TOEIC 2-skills papers exist, so grading is objective-key comparison, synchronous, inside the submit transaction.
- **Concurrency:** `@Version` on `ExamAttempt` (double submit; manual submit racing the expiry close) - 409 `CONCURRENT_UPDATE` is already mapped. `unique (exam_attempt_id, question_id)` on `attempt_answers` already stops concurrent answers to one question doubling up. A partial unique index gives one in-progress attempt per learner per paper. Placement-once is guaranteed by `learner_profiles.placement_attempt_id unique`, set-once, owned by `user` - this module adds no second mechanism.
- **Expiry is closed lazily:** a timed attempt whose `expires_at` has passed is submitted and scored the next time it is touched. No scheduler. A scheduled sweep is the upgrade path if abandoned attempts blocking the in-progress index becomes a real complaint.
- **`certificate_type` means something different here than in `user`.** `user.CertificateType` is one combined picker (`IELTS_GENERAL`, `TOEIC_2_SKILLS`, ...); this module splits it into `certificate_type` + `certificate_variant`, and the split is load-bearing - `grading_criteria` is keyed on type *without* variant, `score_conversions` *with* it. Two enums and a translation at the call boundary, never a shared enum in `shared/` - same decision as `TargetSkill` vs `questions.skill_type`. Today the only translation needed is `TOEIC_2_SKILLS -> (TOEIC, 2_SKILLS)`.
- **Talks to `user` via:**
  - module API - `user` calls in to start the placement attempt (`exam_type=PLACEMENT`) and to read the per-skill result record (`skillType`, `correctCount`, `questionCount`)
  - domain event - this module publishes "attempt scored" after commit; `user` listens and, when the id matches `placement_attempt_id`, writes `assessed_level` -> `current_level` and `converted_score` -> `current_score`
  - ID only, no `@ManyToOne` across the boundary. This module never calls `user`, so there is no cycle.
- **Read path:** repository for lookups, plus `exam/query/` (read-only) for two reads that a repository cannot express - the per-skill breakdown (`attempt_answers` joined to `questions`, grouped by `skill_type`) and loading the paper tree for a sitting with answer keys excluded. Both join only exam-owned tables, so **no cross-module read exception is needed**. No jOOQ or QueryDSL on the classpath, so these are JPQL or native SQL with record projections; `open-in-view` is false, so the whole tree loads inside the transaction.
- **Why:** the sitting is one consistency boundary - start, answer, submit, grade, convert - and every table it writes is this module's. Cutting authoring, grading, or scoring out would each hand another module a write on `attempt_*`, which is the definition of a wrong boundary here. Content and attempts stay together because grading needs the answer keys directly, not through a service call.
- **Revisit if:** authoring grows enough of its own vocabulary and lifecycle to diverge (its own review workflow, versioning rules, contributor roles) - that is the line to split along, and writes were already owned by one path so the split stays tractable. Also revisit when AI grading arrives with a second consumer for `ai_jobs`, or when a question bank makes papers generated rather than authored.

### Schema changes for this module (decided, not yet applied)

`V008`-`V024` created every table above and no environment holds data, so these fold back into the
migration that created the column rather than becoming a chain of `alter table`.

- `exams.exam_type` values -> `PLACEMENT`, `MOCK`. The third value was never a kind of paper.
- `exam_attempts.attempt_mode varchar(20) not null` - `FULL` / `CUSTOM`. Names proposed, not confirmed.
- new `attempt_section_parts (exam_attempt_id, section_part_id)`, primary key on both - which parts a custom sitting covers. Nothing in the current schema can express this.
- `exam_attempts.expires_at` -> **nullable**. An untimed sitting is allowed, and the expiry path skips those rows.
- `exam_attempts.version bigint not null default 0` - the optimistic lock above.
- partial unique index on `exam_attempts (user_id, exam_id) where status = 'IN_PROGRESS'`.

## Deliberately left open

- **Quiz** - tables not designed. It gets its own module when built (own tables, own admin CRUD); the `user` module calls into it. Not folded into `user`, not folded into `exam`.
- **Exam authoring endpoints, and the authorization they need.** Content is seeded by SQL in this phase. Nothing enforces roles today: there is no `@PreAuthorize` anywhere, JWT claims are never mapped to authorities, and `users.role` is a `String` no code reads - `@EnableMethodSecurity` and the `AccessDeniedException` handler are pre-wired hooks waiting for it. Exam authoring is the first feature that needs a real role check, so it will need a `Role` enum plus a `JwtAuthenticationConverter` (or a service-level check against `User.role`) before it works.
- **AI grading**, and with it `grading_criteria`, `attempt_answer_criterion_scores` and `exam_sections.is_scored_by_criteria` - all owned by `exam`, none used. Only TOEIC 2-skills papers exist, so every answer is objective-key gradable.
- **`question_sets.is_single_use`** - the column implies a question-bank / reuse concept that no decision covers. Decide what it means before anything reads it.
- **`spring.servlet.multipart.max-file-size: 2MB`** - too small for speaking recordings. Irrelevant until a 4-skills paper exists; the choice then is raising the limit or presigned direct-to-S3 upload.
- **`@Version` columns** - the design calls for them on `LearnerProfile` and `ExamAttempt`; neither exists in a migration or an entity yet.
- Concrete values of the `TargetSkill` enum vs `questions.skill_type`, especially values that exist on only one side (e.g. Pronunciation). The `skill_type -> TargetSkill` mapping lives in the `user` module; the enum is never shared between modules.
- Unknown values in `user_target_skills.skill` once the FK is gone - decide between ignoring them on read or a cleanup migration, when a value is actually removed.
- Snapshotting `skill_type` into `attempt_answers` - not done. The recommendation is consumed immediately during onboarding, so later reclassification is harmless. Revisit when per-skill progress over time becomes a feature and old results must keep their meaning.
