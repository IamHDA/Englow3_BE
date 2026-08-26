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
  - `shared/security`: `CurrentUser` - exposes only what the Supabase JWT carries. A Supabase **Custom Access Token Hook** copies `englow3.users.id` and `users.role` into the token, so the claims are `sub` (`authProviderId`), `email`, `userId` and `role`. `users.role` stays the source of truth and the token is a copy, so granting or revoking a role takes effect only at the next token refresh. `shared/security` maps the role claim to a Spring authority (`JwtAuthenticationConverter`) and knows no `Role` type - the enum lives in `user`, which owns the column.
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
  6. `COMPLETED` - the step is the only completion flag; there is no separate boolean column
- **Why:** onboarding owns no table of its own; it only fills tables that this module must own anyway, because the same rows are edited outside onboarding (profile screen). A separate onboarding module would mean two writers on `learner_profiles` and `user_learning_purposes`. Role is not a boundary either, so admin catalog management is a controller here, not a module.
- **Revisit if:** onboarding grows tables of its own (persisted recommendations, complex wizard state, flow variants for A/B) - that is the signal to split it out.

### Schema changes for this module

Migrations V001-V027 have run; never edit them. The `target_skills` drop, the `onboarding_step varchar(30)` type and the `learning_purposes.purpose_code` unique constraint were folded back into V007 / V002 / V004 before those ran, so they have no migration of their own. Rewriting a migration in place stops here: from now on Flyway checksums turn an edited migration into a startup failure, so every change needs a new version. (An earlier version of this file also claimed a `target_certificate_type` check constraint in V003; there is none - the column is guarded by the `CertificateType` enum only.)

- **`CertificateType` values** -> `IELTS`, `TOEIC`. **No migration for the column** - `target_certificate_type` is a plain `varchar(20)` with no check constraint, so it takes the new values as they are. Any row still holding `TOEIC_2_SKILLS` / `TOEIC_4_SKILLS` needs a one-off data update, now that the migrations have run. The enum names the certificate a learner is aiming at and nothing finer; every finer distinction is `exam.certificate_variant`'s job. That holds on both sides: IELTS Academic and General share one band scale, so the goal reads the same either way, and TOEIC L&R vs S&W could not be a goal here at all - `learner_profiles` has a single `target_score` and those are two scales. This is a **frontend-visible rename**, the picker drops from four options to two. **Revisit if** the Academic/General choice needs to filter which IELTS papers a learner is shown, or when an S&W paper makes `target_score` ambiguous - either adds a target-variant column here, not a value to this enum.
- **V026** - `learner_profiles.placement_attempt_id uuid unique references exam_attempts (id) on delete restrict`. One row per learner already, so one column means at most one placement attempt - no partial index, and the constraint sits in a table this module owns. The `unique` is what makes placement-once a database guarantee rather than an application check, so `exam` relies on it instead of adding a second mechanism.
- **V027** - `users.gender` and `users.birth_date`.
- **V028** - drop `users.is_onboarding_completed`; `onboarding_step = COMPLETED` is the single source of truth, a boolean beside it could disagree with it.
- **R__seed_learning_purposes.sql** - repeatable seed for the catalog (`CERTIFICATE`, `COMMUNICATION`, `WORK`, `STUDY_ABROAD`, `SCHOOL`, `TRAVEL`). Only `CERTIFICATE` is load-bearing (it drives the branch); the rest is placeholder content, edit the file and it re-applies.

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
- **One paper = one reported score.** That, not the skill count, is what decides whether a certificate needs several papers. TOEIC L&R (10-990) and TOEIC S&W (0-400) are two scores, so two papers and two attempts - `exam_attempts.converted_score` is a single column and cannot hold both, `score_conversions` is keyed with `certificate_variant` so the two scales need two variants, and `is_scored_by_criteria` differs between them, which would split submit-and-grade down the middle of one paper. IELTS and VSTEP report one overall band across four skills, so each is one paper with four sections. `certificate_variant` values: `LR` / `SW` for TOEIC, `ACADEMIC` / `GENERAL` for IELTS - not `2_SKILLS`, because S&W is also two skills and the count stops distinguishing anything the moment it exists. **"2 kỹ năng / 4 kỹ năng" is a label derived** from the set of `exam_sections.section_type`, never a stored column - a `skill_coverage` column beside `certificate_variant` would be a second source of truth nothing keeps in step, and would allow `IELTS + 2_SKILLS`.
- **Sitting mode belongs to the attempt, not the paper:** choosing which `section_parts` to do out of one full paper, with a freely chosen time limit, is a *way of sitting* a paper rather than a kind of paper. So `exam_attempts.attempt_mode` (`FULL` / `CUSTOM`) plus `attempt_section_parts` carries it, and `exams` stays untouched. `exam_attempts.max_raw_score` and `question_count` are already per-attempt columns, so a partial sitting needs nothing further - they are computed from the selected parts at start.
- **Grading lives here, not in a module of its own.** It writes `attempt_answers`, `attempt_section_results` and `exam_attempts`, all exam-owned; splitting it would put a second module in charge of writing this module's tables and would cut submit-and-auto-grade, one consistency boundary, in half. In this phase only TOEIC 2-skills papers exist, so grading is objective-key comparison, synchronous, inside the submit transaction.
- **Concurrency:** `@Version` on `ExamAttempt` (double submit; manual submit racing the expiry close) - 409 `CONCURRENT_UPDATE` is already mapped. `unique (exam_attempt_id, question_id)` on `attempt_answers` already stops concurrent answers to one question doubling up. A partial unique index gives one in-progress attempt per learner per paper. Placement-once is guaranteed by `learner_profiles.placement_attempt_id unique`, set-once, owned by `user` - this module adds no second mechanism.
- **Expiry is closed lazily:** a timed attempt whose `expires_at` has passed is submitted and scored the next time it is touched. No scheduler. A scheduled sweep is the upgrade path if abandoned attempts blocking the in-progress index becomes a real complaint.
- **`certificate_type` means something different here than in `user`.** `user.CertificateType` is a goal-level picker (`IELTS`, `TOEIC`); this module splits it into `certificate_type` + `certificate_variant`, and the split is load-bearing - `grading_criteria` is keyed on type *without* variant, `score_conversions` *with* it. Two enums and a translation at the call boundary, never a shared enum in `shared/` - same decision as `TargetSkill` vs `questions.skill_type`. The translation is deliberately not 1:1: `IELTS -> (IELTS, any variant)`, `TOEIC -> (TOEIC, any variant)`. The goal names the certificate; this module decides which papers that makes eligible - today `ACADEMIC` + `GENERAL` for IELTS, `LR` for TOEIC.
- **Talks to `user` via:**
  - module API - `user` calls in to start the placement attempt (`exam_type=PLACEMENT`) and to read the per-skill result record (`skillType`, `correctCount`, `questionCount`)
  - domain event - this module publishes "attempt scored" after commit; `user` listens and, when the id matches `placement_attempt_id`, writes `assessed_level` -> `current_level` and `converted_score` -> `current_score`
  - ID only, no `@ManyToOne` across the boundary. This module never calls `user`, so there is no cycle.
- **Read path:** repository for lookups, plus `exam/query/` (read-only) for two reads that a repository cannot express - the per-skill breakdown (`attempt_answers` joined to `questions`, grouped by `skill_type`) and loading the paper tree for a sitting with answer keys excluded. Both join only exam-owned tables, so **no cross-module read exception is needed**. No jOOQ or QueryDSL on the classpath, so these are JPQL or native SQL with record projections; `open-in-view` is false, so the whole tree loads inside the transaction.
- **Why:** the sitting is one consistency boundary - start, answer, submit, grade, convert - and every table it writes is this module's. Cutting authoring, grading, or scoring out would each hand another module a write on `attempt_*`, which is the definition of a wrong boundary here. Content and attempts stay together because grading needs the answer keys directly, not through a service call.
- **Revisit if:** authoring grows enough of its own vocabulary and lifecycle to diverge (its own review workflow, versioning rules, contributor roles) - that is the line to split along, and writes were already owned by one path so the split stays tractable. Also revisit when AI grading arrives with a second consumer for `ai_jobs`, or when a question bank makes papers generated rather than authored.

### Admin exam management

The first admin feature. It is a controller in this module, not a module of its own - role is not
a boundary. First slice: **list + create**. Authoring the content inside a paper stays deferred;
content still arrives by SQL seed.

- **Authorization:** `@PreAuthorize("hasRole('ADMIN')")` on `/api/admin/exams/**`, backed by the
  `role` claim the Supabase token hook carries (see `shared + config`). `exams.created_by_user_id`
  comes from `CurrentUser.userId()`, also a claim. **This module still never calls `user`** - the
  claim is what keeps that true, and that is the reason for putting the internal id in the token
  rather than resolving it per request.
- **Create makes a DRAFT shell only** - title, description, `exam_type`, `certificate_type` +
  `certificate_variant`, `target_level`, `duration_seconds`, `max_raw_score`, `pass_score`. No
  sections. `status` is `DRAFT` / `PUBLISHED` / `ARCHIVED`, `version_number` starts at 1,
  `published_at` stays null. `max_raw_score` is entered rather than derived, because sections do
  not exist yet at create time; `publish()` is what makes the two agree.
- **`Exam` is the one content entity with rules.** `Exam.draft(...)` assigns its own `UUID`;
  `publish(long questionCount, BigDecimal sectionsRawTotal)` refuses a paper with no section, no
  question, or whose section scores do not sum to `max_raw_score`, and is the only thing that sets
  `status` and `published_at`. Plain values in - the service runs the counts, the entity touches no
  repository. No setter for `status`, `publishedAt`, `versionNumber`, and no Lombok `@Setter` /
  `@Data` on it, which would regenerate exactly those. `ExamSection`, `SectionPart`, `QuestionSet`,
  `Question` stay plain read-only entities - authoring is supporting, not core.
- **Read path:** the admin list lives in `exam/query/` - optional filter combinations (status,
  exam_type, certificate_type / variant, target_level, title search) plus paging, plus per-row
  section count, question count and attempt count. Every table joined is exam-owned, so no
  cross-module read exception is needed. The question count is a four-join descent
  (`exam_sections -> section_parts -> question_sets -> questions`); acceptable on one page of rows,
  and it is the same figure `publish()` needs. Not denormalised onto `exams`, where it would rot.
- **The creator's name is not resolved server-side.** The list returns `createdByUserId` and the
  frontend composes it against the admin user list. One extra call on an admin screen costs less
  than the first `exam -> user` edge, and admins are few enough to cache.
- **PDF of a paper is printed by the browser, not generated here.** The admin detail screen renders
  the paper and a `@media print` stylesheet turns Ctrl+P into a PDF. No endpoint, no PDF
  dependency, no embedded font - and no risk of losing Vietnamese diacritics, which is the usual
  way server-side PDF goes wrong. What this module owes it is the paper-tree read below.
  **Revisit if** batch export, emailing, watermarking, or a byte-identical file is needed; the
  expensive parts (the tree loader, the layout) would already be done, only the renderer moves.
- **`exam/query/` owes two tree loads, not one:** the sitting loads the paper with answer keys
  **excluded**, the admin detail loads it with keys and `explanation` **included**. Same descent,
  different projection - and the admin one is what both the detail screen and its printed form use.
- **Editing a paper after publish** is not decided. `exam_attempts.exam_version_number` snapshots
  the paper, so a content edit must either bump `version_number` or be refused. It cannot happen
  yet because nothing can edit content; decide it with the authoring endpoints.

### Schema changes for this module (decided, not yet applied)

`V008`-`V024` created every table above and **have already run**, so none of the changes below may
be folded back into them - each needs a new migration from V030 on. (An earlier version of this
file assumed they could still be rewritten in place; that stopped when the migrations ran.)

- `exams.exam_type` values -> `PLACEMENT`, `MOCK`. The third value was never a kind of paper.
- `exam_attempts.attempt_mode varchar(20) not null` - `FULL` / `CUSTOM`. Names proposed, not confirmed.
- new `attempt_section_parts (exam_attempt_id, section_part_id)`, primary key on both - which parts a custom sitting covers. Nothing in the current schema can express this.
- `exam_attempts.expires_at` -> **nullable**. An untimed sitting is allowed, and the expiry path skips those rows.
- `exam_attempts.version bigint not null default 0` - the optimistic lock above.
- partial unique index on `exam_attempts (user_id, exam_id) where status = 'IN_PROGRESS'`.
- `exams.status` values -> `DRAFT` / `PUBLISHED` / `ARCHIVED`, `certificate_variant` values -> `LR` / `SW` / `ACADEMIC` / `GENERAL`. Both columns already exist as `varchar` with no check constraint, so neither needs a migration; the enums guard them.
- **V029 is written and on disk**, unlike everything above it, which is decided but has no migration file yet: `question_sets.content` and `question_sets.metadata`, `question_options.explanation`. The TOEIC delivery package carries Part 6/7 passage text per group, an audio script with cues, and a Vietnamese rationale for every single option - and the schema had nowhere to put any of the three. All nullable, no backfill.

## Deliberately left open

- **Quiz** - tables not designed. It gets its own module when built (own tables, own admin CRUD); the `user` module calls into it. Not folded into `user`, not folded into `exam`.
- **Exam authoring endpoints.** Content is seeded by SQL in this phase. Admin can create a paper shell and publish it, but cannot fill it through the API - sections, parts, question sets and questions have no write path. Authorization is no longer open (see `shared + config` and *Admin exam management*): `Role` enum in `user`, the Supabase token hook, `JwtAuthenticationConverter`, `@PreAuthorize`. What is still open there is operational - granting the hook read access to `englow3.users`, and what an admin does when their token was issued before the role was granted (today: sign out and back in).
- **AI grading**, and with it `ai_jobs`, `grading_criteria`, `attempt_answer_criterion_scores`, `exam_sections.is_scored_by_criteria` and Redis - all owned by `exam`, none used. Only TOEIC 2-skills papers exist, so every answer is objective-key gradable. `ai_jobs` stays a one-consumer table until a second consumer justifies pulling it out.
- **`question_sets.is_single_use`** - the column implies a question-bank / reuse concept that no decision covers. Decide what it means before anything reads it.
- **`spring.servlet.multipart.max-file-size: 2MB`** - too small for speaking recordings. Irrelevant until a 4-skills paper exists; the choice then is raising the limit or presigned direct-to-S3 upload.
- **`@Version` columns** - the design calls for them on `LearnerProfile` and `ExamAttempt`; neither exists in a migration or an entity yet.
- Concrete values of the `TargetSkill` enum vs `questions.skill_type`, especially values that exist on only one side (e.g. Pronunciation). The `skill_type -> TargetSkill` mapping lives in the `user` module; the enum is never shared between modules.
- Unknown values in `user_target_skills.skill` once the FK is gone - decide between ignoring them on read or a cleanup migration, when a value is actually removed.
- Snapshotting `skill_type` into `attempt_answers` - not done. The recommendation is consumed immediately during onboarding, so later reclassification is harmless. Revisit when per-skill progress over time becomes a feature and old results must keep their meaning.
