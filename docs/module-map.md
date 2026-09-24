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
- **Derived, not stored:** the experience counter and level are computed from the
  activity tables on every read. There is no points ledger to drift out of step
  with the work it counts.

## `ai`

Owns the durable job queue that every provider call goes through. It owns no
business vocabulary of its own: it knows a job has a type, a target and a payload,
and nothing about what any of those mean.

- **Tables:** `ai_jobs`.
- **Entry points:** none. Other modules enqueue; nothing outside the worker reads.
- **Dispatch:** a handler registers for one `AiJobType` and never sees the others,
  which is what let a second kind of work be added without touching the worker.
- **Retry:** the queue decides whether a failure is worth repeating; the handler
  reports which kind it was. A transient failure is left pending rather than shown
  to the learner as failed.
- **Claiming:** `for update skip locked`, so two workers never take the same row.

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
- **Quota:** shares `learning`'s daily provider limit rather than holding its own.
  Both spend the same budget, and two ceilings that have to be reasoned about
  together are worse than one that bounds the total.
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
