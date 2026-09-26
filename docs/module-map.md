# Module Map

This file records current ownership boundaries in the backend. Coding conventions
and package structure live in [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## `user`

Owns authentication-linked identity, learner profile, learning purposes, target
skills, and onboarding.

- **Tables:** `users`, `learner_profiles`, `learning_purposes`,
  `user_learning_purposes`, `user_target_skills`.
- **Entry points:** profile and onboarding APIs.
- **Authentication:** Supabase issues JWTs. The backend maps the token subject to
  `users.auth_provider_id`; application code does not manage passwords or sessions.
- **Cross-module reference:** `learner_profiles.placement_attempt_id` stores an exam
  attempt UUID. It is not a JPA relationship to the exam module.
- **Public API:** `user.api.UserDirectory` and `user.api.PlacementRecorder` are the
  contracts for cross-module identity lookup and placement recording. User entities,
  repositories, and service implementations remain internal.
- **Files:** profile images use the shared storage client, while object-key meaning
  remains owned by this module.

## `exam`

Owns exam authoring, paper structure, questions, scoring configuration, attempts,
answers, and section results.

- **Tables:** `exams`, `exam_sections`, `section_parts`, `question_sets`,
  `question_set_options`, `questions`, `question_options`,
  `question_matching_answers`, `question_accepted_answers`, `grading_criteria`,
  `score_conversions`, `exam_attempts`, `attempt_section_results`,
  `attempt_answers`, `attempt_answer_options`, and
  `attempt_answer_criterion_scores`.
- **Entry points:** administrator authoring APIs, learner catalogue and safe paper
  delivery, plus the start, submit, grade, and result lifecycle for exam attempts.
- **Read model:** ordinary reads belong in `ExamRepository`; complex paper assembly
  may use the module's read-only `query/` package.
- **Files:** question media and private attempt media are exam-owned even when the
  shared storage client performs the I/O.

## `learning`

Owns everyday practice: vocabulary sets and their spaced-repetition schedule,
quizzes and quiz attempts, dictation lessons and transcription attempts, and the
daily path that assembles all three into a plan.

- **Tables:** `flashcard_sets`, `flashcards`, `flashcard_reviews`,
  `flashcard_review_logs`, `quizzes`, `quiz_questions`, `quiz_question_options`,
  `quiz_question_tokens`, `quiz_question_pairs`, `quiz_attempts`,
  `quiz_attempt_answers`, `dictation_lessons`, `dictation_sentences`, and
  `dictation_attempts`.
- **Entry points:** administrator authoring APIs, learner catalogues, the study,
  sitting and submission lifecycles, the per-feature statistics screens, and
  `GET /api/daily-path`.
- **Import:** this module owns the two use cases that bring generated content in -
  flashcard batches and shadowing batches. Both check before they write, both land
  in a draft, and both hold to the generator's schema through a contract test. See
  the data pipeline boundary below for why they exist.
- **Read model:** ordinary reads belong in the module's repositories; the grouped
  aggregates behind the statistics screens and the daily path live in its read-only
  `query/` package.
- **Answer keys:** a paper delivered mid-attempt carries no correct answer. Each
  audience gets its own projection rather than a shared record with fields blanked
  out.
- **Cross-module read:** `DailyPathQuery` reads `exam_attempts` to count study days
  and experience points. Read-only, declared here, and deliberate: a streak that
  ignored exams would tell a learner who spent two hours on a mock paper that they
  had not studied. No `learning` code writes an exam table.
- **Cross-module read:** `AdminOverviewQuery`, behind `GET /api/admin/overview`,
  counts `speaking_prompts`, `exams`, `exam_attempts` and `users` alongside this
  module's own tables - drafts, items waiting on review and published items per
  kind of content, and learner activity over the last week. It lives here
  because content review does; it writes nothing, and each count is one it
  could not get through another module's service without twenty calls.
- **Derived, not stored:** the experience counter and level are computed from the
  activity tables on every read. There is no points ledger to drift out of step
  with the work it counts.

## `ai`

Owns the durable job queue that every provider call goes through. It owns no
business vocabulary of its own: it knows a job has a type, a target and a payload,
and nothing about what any of those mean.

- **Tables:** `ai_jobs`.
- **Entry points:** none. Other modules enqueue; nothing outside the worker reads.
- **Public API:** `ai.api.AiJobQueue` exposes enqueue/quota capabilities and
  `ai.api.AiJobHandler` is the handler contract. `AiJob`, `AiJobType`, and
  `AiJobStatus` are internal AI models; they do not cross module boundaries.
- **Dispatch:** the worker maps internal `AiJobType` values to a handler's stable
  job-type string; handlers do not see the internal enum.
- **Retry:** the queue decides whether a failure is worth repeating; the handler
  reports which kind it was. A transient failure is left pending rather than shown
  to the learner as failed.
- **Claiming:** `for update skip locked`, so two workers never take the same row.
- **Daily budget:** owned here, because every provider call passes through this
  table. Each job records who asked (`requested_by_user_id`), and speaking and the
  tutor both ask the queue rather than counting their own work - which is what they
  used to do, each against the full limit, letting a learner spend it twice.
- **Giving up:** when a job ends without a result - a refusal, the last retry, a
  stall reclaimed once too often, or the handler throwing - the worker calls the
  handler's `onGaveUp`, and that is the only place the learner is told. Telling
  them from inside a handler's run covered one of those four and left the other
  three waiting forever.

## `speaking`

Owns pronunciation practice: what there is to say, the recording of someone saying
it, and what the assessment made of it.

- **Tables:** `speaking_prompts`, `speaking_attempts`, `speaking_attempt_words`.
- **Entry points:** the learner catalogue and attempt lifecycle, plus the admin
  authoring API.
- **Review workflow:** the same one `learning`'s three content types run, through
  its own status enum. Persistence types do not cross module boundaries, so the
  review columns are repeated here rather than borrowed.
- **Files:** recordings go from the browser straight to object storage through a
  presigned PUT bound to the declared size; nothing streams through the API.
- **AI:** enqueues through `ai`. It never calls a provider itself.

## `tutor`

Owns the AI tutor conversation.

- **Tables:** `tutor_conversations`, `tutor_messages`.
- **Entry points:** `/api/tutor`, every route scoped to the caller.
- **Asking is synchronous, answering is not:** the question is stored and queued,
  and the screen polls the turn it created. A question is written before anything
  is asked of a provider, so a provider outage never loses what the learner typed.
- **Quota:** asks `ai` whether the learner has allowance left, and says no in its
  own words if not. It does not count its own work: the budget is shared with
  speaking, and only the queue sees both.
- **AI:** enqueues through `ai`. It never calls a provider itself.

## `shared` and `config`

These are technical packages, not business modules, and own no business tables.

- `shared/error` owns the common error response and HTTP exception translation.
- `shared/security` exposes authenticated token information without owning user
  roles or profile data.
- `shared/storage` provides generic object-storage operations without business key
  conventions.
- `shared/page` and `shared/logging` contain transport-level infrastructure.
- `config` wires security, OpenAPI, and storage clients.

Business enums, business DTOs, authorization decisions, and table writes do not
belong in `shared`.

## AI boundary

`ai_service/` is a stateless FastAPI adapter for LLM, embedding, and speech
providers. It owns provider credentials and provider-specific HTTP contracts. It
does not own public APIs, users, business rules, or database tables.

Migration `V023__create_ai_jobs_table.sql` created `ai_jobs`, and the `ai` module
above now owns it. Speech assessment and tutor replies both ride that queue;
neither module calls a provider directly, and no module outside `ai` writes the
table.

Neither pipeline has ever run against a real provider - there is no key for either
Azure Speech or an LLM. Every decision they make about a provider answer is tested;
the call itself is not.

Frontend and mobile clients must call the Spring API, never FastAPI directly.

## Data pipeline boundary

`data_pipeline/` is an offline authoring and validation toolchain, not a runtime
Spring module. It owns generators, schemas, validators, local output, and QA reports.
Runtime ingestion must go through an explicitly owned backend use case; the pipeline
does not become a second writer to application tables. Two such use cases exist, both
owned by `learning`: `POST /api/admin/flashcards/import` and
`POST /api/admin/dictation/import`. Each offers a dry run first, because the check has
to be visible before anything is saved, and each writes only into a draft - generated
content is not reviewed content, and an import that could reach a published set would
be a way around the review workflow.

The importers read the generator's schema files directly in their tests. The two sides
live in one repository and otherwise never meet until someone uploads a file; the
flashcard importer was once written against a guessed shape, and its own tests agreed
with it because both came from the same guess.

## Adding ownership

Before introducing a table or module:

1. Confirm that an existing module does not already own the capability.
2. Name exactly one writer for every new table.
3. Record cross-module reads and IDs here.
4. Add schema changes through a new Flyway migration. Never edit one that has run:
   Flyway stores a checksum, and a rewritten migration stops every environment that
   already applied it from starting. `V010`/`V012` were rewritten in place once,
   while both tables were still empty everywhere - that is the only condition under
   which it is survivable, and it forced a full schema drop and re-migrate in every
   environment. Check `flyway_schema_history`, not the migrations folder, for what
   has actually run.
5. Keep runtime provider integration behind the AI boundary described above.

## Still open

Carried from the longer version of this file. Entries it held that described the
`ai` module as removed, `ai_jobs` as unowned, or quiz tables as undesigned are
gone rather than kept: all three describe a state that no longer exists.

- **Importing exam questions** from CSV/XLSX/DOCX. Flashcards and shadowing clips
  now have importers; questions do not, and they are the larger job - a parsing
  library, a column contract, and per-row error reporting.
- **AI-generated exam questions.** The `ai` module that would carry them exists
  now, so this is a handler and a prompt rather than a module.
- **AI grading** of anything the objective answer key cannot mark.
  `grading_criteria`, `attempt_answer_criterion_scores` and
  `exam_sections.is_scored_by_criteria` are exam-owned and unused by the current
  flow.
- **`@Version` columns** on `LearnerProfile` and `ExamAttempt`. The design calls
  for them; neither a migration nor an entity has one.
- **`spring.servlet.multipart.max-file-size`** is 12MB, raised for exam listening
  audio. Speaking no longer needs it raised further - recordings go straight to
  object storage through a presigned PUT and never pass through this application -
  but a 4-skills paper authored through the admin API still would.
- **`TargetSkill` against `questions.skill_type`**, where a value exists on only
  one side. The mapping lives in `user`; the enum is never shared across modules.
- **Unknown values in `user_target_skills.skill`** once the foreign key is gone:
  ignore on read, or a cleanup migration. Decide when a value is actually removed.
- **Snapshotting `skill_type` into `attempt_answers`** - not done. The
  recommendation is consumed during onboarding, so later reclassification is
  harmless today. Revisit when per-skill progress over time has to keep its
  meaning.
