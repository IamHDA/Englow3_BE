# Module Map

This file records current ownership boundaries in the backend. Coding conventions
and package structure live in [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## `user`

Owns authentication-linked identity, learner profile, learning purposes, target
skills, and onboarding.

- **Tables:** `users`, `learner_profiles`, `learning_purposes`,
  `user_learning_purposes`, `user_target_skills`, `user_tour_completions`.
- **Entry points:** profile, onboarding, and role-specific website tour APIs.
- **Bootstrap read:** `/api/user/me/bootstrap` returns the caller's account and
  learner onboarding snapshot in one transaction. It does not create missing
  profiles, cache another account's data, or load learner state for Staff/Admin.
- **Authentication:** Supabase issues JWTs. The backend maps the token subject to
  `users.auth_provider_id`; application code does not manage passwords or sessions.
- **Cross-module reference:** `learner_profiles.placement_attempt_id` stores an exam
  attempt UUID. It is not a JPA relationship to the exam module.
- **Public API:** `user.api.UserDirectory` and `user.api.PlacementRecorder` are the
  contracts for cross-module identity lookup and placement recording.
  `PlacementRecorder.recordLevelCheck` records a level measured by the roadmap's
  level check; `user.api.LearnerGoalReader` hands the roadmap the goal, level,
  daily study minutes and purposes as plain values. User entities,
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
  `attempt_answer_criterion_scores`, and `exam_attempt_drafts`.
- **Deadline:** draft replacements lock the owning attempt and check the server
  deadline plus draft revision. A scheduled, idempotent finalizer grades only
  the last accepted snapshot when a browser is absent. Result reads also finalize
  expired live attempts; late client answers never enter the graded snapshot.
- **Entry points:** administrator authoring APIs, learner catalogue and safe paper
  delivery, plus the start, submit, grade, and result lifecycle for exam attempts.
- **Modes:** one active attempt per learner, paper and mode. FULL and PRACTICE
  may remain open independently; replacing one never finalizes the other.
  Active-attempt previews are read-only and do not start or reset a clock.
- **Level checks:** `exam_type = LEVEL_CHECK` papers are full-sitting only and
  stay out of the learner catalogue. `exam.api.ScoredExamAttemptReader` reports a
  learner's sitting as per-section points for the roadmap's milestones and level
  checks, without exposing exam entities.
- **Answer shapes:** `SINGLE_CHOICE`, `MULTIPLE_CHOICE` ("choose N", a share per
  correct option, never more than N chosen) and `TEXT_ENTRY` (typed, graded against
  `question_accepted_answers` with only case and whitespace forgiven). The rules live in
  `helper.AnswerKey`. `MATCHING` picks one entry of the list its question set shares
  (`question_set_options`, keyed by `question_matching_answers`); the learner's paper
  carries the list but never the key, and the choice is stored in
  `attempt_answer_options.question_set_option_id`.
- **Question numbers:** computed once by `helper.QuestionNumbering` over the whole
  paper and sent with the paper and the review; screens do not count. IELTS restarts
  at 1 in each section, TOEIC counts on.
- **Bands:** each scored attempt writes one `attempt_section_results` row per section.
  A full section whose points the certificate's `score_conversions` table covers gets
  a band (`helper.BandConversion`); a practice never does. `score_conversions` is
  reference data seeded by migration and never written by the application.
- **Read model:** ordinary reads belong in `ExamRepository`; complex paper assembly
  may use the module's read-only `query/` package.
- **Files:** question media and private attempt media are exam-owned even when the
  shared storage client performs the I/O.
- **IELTS full test:** groups one full Listening + Reading sitting of a `MOCK` paper
  with Writing Task 1, Writing Task 2 and Speaking Parts 1-3 from `assessment` into
  one sitting with an overall band.
  - **Who sits it:** it is the IELTS learner's placement test, and is also in the
    catalogue without a roadmap (`/api/full-tests`). Only learners aiming at IELTS may
    start one (`user.api.LearnerGoalReader`); TOEIC learners keep the placement paper.
  - **Tables:** `full_tests` and `full_test_sittings` (V082). Only exam writes them.
    The five tasks are assessment's and are referenced by id only.
  - **Rules:** `FullTest` follows a paper's review workflow and can be published only
    when its paper is a published IELTS mock of its variant with Listening and Reading
    and all five tasks are published IELTS tasks of the right slot. `FullTestSitting`
    attaches the latest full scored sitting of the paper and the latest submitted
    answer to each task started since the sitting, if submitted within seven days, and
    never swaps one once attached. The provisional band is the lower of Listening and
    Reading; Writing weights Task 2 twice, Speaking averages its parts, and the overall
    band is the IELTS mean of four (`helper.IeltsBand`).
  - **Reads and events:** Writing/Speaking bands and task facts come from
    `assessment.api.AssessmentBandReader`. `worker.FullTestSittingListener` settles a
    learner's open sittings after `ExamSittingScored` or `AssessmentAnswerGraded`
    commits; those events are not durable, so reads and `FullTestSittingSweeper`
    (every 10 minutes, `app.exam.full-test-sweep.*`) catch up and close sittings past
    their deadline.
  - **Concurrency:** a sitting is settled under a row lock with `@Version`; one open
    sitting per learner and full test is a partial unique index.
  - **Results out:** both bands reach the learner profile through
    `user.api.PlacementRecorder.recordIeltsBand`. `exam.api.FullTestCompleted` announces
    a final band; the roadmap does not listen to it yet.
  - **Why:** the full test must exist without a roadmap, and exam already owns mock
    papers and sittings. The grouping has no rules beyond combining bands, so a module
    of its own would only add wiring. `assessment` does not depend on `exam`, so the
    new dependency makes no cycle.
  - **Revisit if:** the full test gains rules of its own (retake policy, pricing, a
    review workflow unlike a paper's), or `assessment` ever needs to read exam data.

## `flashcard`

Owns flashcard authoring, vocabulary sets and cards, spaced repetition, review
history, learner statistics, and flashcard batch import.

- **Tables:** `flashcard_sets`, `flashcards`, `flashcard_reviews`, and
  `flashcard_review_logs`. Only this module writes them.
- **Entry points:** administrator authoring/review/import APIs, learner catalogue,
  study/rating lifecycle, and flashcard statistics.
- **Import:** `flashcard.helper.FlashcardImport` validates generated batches and
  `AdminFlashcardService` writes accepted cards into a draft set.

## `quiz`

Owns quiz authoring, all five question shapes, attempts, deterministic matching
shuffle, and grading.

- **Tables:** `quizzes`, `quiz_questions`, `quiz_question_options`,
  `quiz_question_tokens`, `quiz_question_pairs`, `quiz_attempts`, and
  `quiz_attempt_answers`. Only this module writes them.
- **Entry points:** administrator authoring/review APIs plus learner catalogue,
  start/resume, submission, and result APIs.
- **Answer keys:** a paper delivered mid-attempt carries no correct answer. Each
  audience gets its own projection rather than a shared record with fields blanked
  out.

## `dictation`

Owns dictation authoring, sentence/audio metadata, attempts, scoring, the mistake
queue, learner statistics, and shadowing batch import.

- **Tables:** `dictation_lessons`, `dictation_sentences`, and
  `dictation_attempts`. Only this module writes them.
- **Entry points:** administrator authoring/review/import APIs plus learner
  catalogue, practice/submission, mistake queue, and statistics APIs.
- **Public API:** `dictation.api.DictationCompletionPolicy` exposes the completion
  rule needed by `progress` without exporting the scorer or its internal types.
- **Import:** `dictation.helper.DictationImport` validates generated batches and
  `AdminDictationService` writes accepted lessons as drafts.

## `progress`

Owns the read-only composition behind Daily Path, XP/level, streaks, daily quests,
and the administrator overview. It owns no business write table and maps no other
module's table as a JPA entity.

- **Entry points:** `GET /api/daily-path` and `GET /api/admin/overview`.
- **Cross-module read:** `DailyPathQuery` reads activity from flashcard, quiz,
  dictation, exam and assessment tables. `AdminOverviewQuery` reads user, speaking, exam,
  flashcard, quiz, dictation and assessment tables. Both are documented read-only joins; no
  `progress` code writes those tables.
- **Productive practice:** a valid first submission on each attempt counts once for study activity and the existing practice-attempt XP rate. Regrading, retrying a grader and reading a report do not add another submission. Drafts count for neither XP nor streak.
- **Roadmap:** with an active roadmap, the outstanding work is cards due, then the
  current stage's unfinished content due by this week of the stage (from
  `roadmap.api.RoadmapTodayReader`), then the stage's milestone once its required
  content is done. Without one, the general rules above choose the work.
- **Derived, not stored:** plans, experience, levels, streaks, and quests are
  computed on every read. There is no points ledger or persisted daily path to
  drift out of step with activity.

## `roadmap`

Owns each learner's plan towards their goal: stages, the content scheduled in each,
the milestone that closes each stage, the three-part level check, and the history
of how the plan changed.

- **Tables:** `learning_roadmaps`, `roadmap_stages`, `roadmap_items`,
  `roadmap_milestones`, `level_checks`, `roadmap_adjustments`,
  `level_check_conversions`, `level_check_weights`. Only this module writes them.
- **Entry points:** `GET/POST/DELETE /api/roadmap`,
  `POST /api/roadmap/milestones/{id}/sittings`, and `/api/roadmap/level-checks`
  (start, latest, read, attach the exam sitting or spoken answer, skip speaking).
- **Generation:** `ROADMAP_GENERATION` jobs on the `ai` queue, outside the
  learner's daily allowance but limited to five a day each. The payload carries the
  goal and the candidate content; `RoadmapGenerationHandler` asks the LLM and
  `helper.RoadmapPlanValidator` refuses any plan that schedules content not offered,
  skips a level, misses the goal, runs past the target date or asks for more minutes
  a week than the learner has. A refused plan is retried; the last failure marks a
  first plan `FAILED` and leaves an adjusted plan as it was. A token on the roadmap
  fences out results of requests that were replaced.
- **Adjustment:** a failed milestone, falling a week behind a stage's planned end,
  or a changed goal of the same kind replaces the stages not yet passed. Passed
  stages are never replaced. A different goal supersedes the plan.
- **Level check:** a `LEVEL_CHECK` exam paper (READING section for vocabulary and
  grammar, plus LISTENING) and a `LEVEL_CHECK` speaking task. Section shares map to
  bands through `level_check_conversions`; speaking through
  `assessment.api.SpeakingLevelReader`. The parts combine as a weighted mean,
  rounded down; without a spoken part the written weights make the whole.
  Weights come from `level_check_weights` (highest-priority row among the
  learner's purposes, else DEFAULT, as in force at the time); tune them by inserting
  rows with a later `effective_from` - from the admin screen
  (`GET/PUT /api/admin/roadmap/level-check-weights`, ADMIN only) or SQL. Each completed
  check keeps the weights it used. The screen lists purposes through a declared
  read-only read of `learning_purposes`. The result is recorded on the
  learner profile through `user.api.PlacementRecorder`.
- **Cross-module read:** `ContentCatalogQuery` reads published
  `flashcard_sets`, `dictation_lessons`, `quizzes`, `speaking_prompts`,
  `assessment_tasks` and `exams`; `ItemProgressQuery` reads `flashcards`,
  `flashcard_reviews`, `dictation_sentences`, `dictation_attempts`,
  `quiz_attempts`, `speaking_attempts` and `assessment_attempts`. Both are
  documented read-only joins; no roadmap code writes those tables.
- **Results without a page view:** `exam` publishes `exam.api.ExamSittingScored`
  and `assessment` publishes `assessment.api.AssessmentAnswerGraded` inside the
  grading transaction. `RoadmapResultListener` hears them after commit and moves
  level checks and mock milestones on at once. `RoadmapResultSweeper` (every 10
  minutes, `app.roadmap.sweep.*`) re-checks unfinished level checks and active
  roadmaps, catching lost announcements and roadmaps a week behind. Reading the
  roadmap or a level check catches up too. Neither publisher depends on `roadmap`.
- **Derived, not stored:** item progress is computed from activity on every read.

## `ai`

Owns the durable job queue that every provider call goes through. It owns no
business vocabulary of its own: it knows a job has a type, a target and a payload,
and nothing about what any of those mean.

- **Tables:** `ai_jobs`.
- **Allowance:** every job counts against the requesting learner's daily allowance
  except `ROADMAP_GENERATION`, which has its own per-learner daily limit.
- **Entry points:** none. Other modules enqueue; nothing outside the worker reads.
- **Public API:** `ai.api.AiJobQueue` exposes enqueue/quota capabilities and
  `ai.api.AiJobHandler` is the handler contract. `AiJob`, `AiJobType`, and
  `AiJobStatus` are internal AI models; they do not cross module boundaries.
- **Dispatch:** the worker maps internal `AiJobType` values to a handler's stable
  job-type string; handlers do not see the internal enum.
- **Latency:** a content-free `AiJobQueued` signal wakes the local worker only
  after commit. Signals are coalesced on the bounded worker executor; completion
  checks for the next job. The scheduled scan still recovers missed signals and
  work from other instances. No provider I/O runs in an enqueue transaction.
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

## `translation`

Owns Vietnamese-to-English translation practice: sets of sentences to translate,
and each learner's graded attempts.

- **Tables:** `translation_sets`, `translation_items`, `translation_attempts`.
- **Entry points:** the learner library, practice and attempt history under
  `/api/translation`, plus the admin authoring API under `/api/admin/translation`.
- **Review workflow:** the same shape as the other authored content, through its
  own status enum. Sentences are replaced wholesale on save, which is safe because
  only a never-published set is editable.
- **Grading:** a `TRANSLATION_ASSESSMENT` job through `ai`, counted against the
  shared daily allowance. `TranslationRubric` validates the model's four part
  scores and computes the total and verdict itself. The reference translation is
  returned only once the attempt is no longer waiting.
- **Account data:** `AccountServiceImpl` includes `translation_attempts` in the
  personal export (declared read-only); attempts cascade with the user row.

## `speaking`

Owns pronunciation practice: what there is to say, the recording of someone saying
it, and what the assessment made of it.

- **Tables:** `speaking_prompts`, `speaking_attempts`, `speaking_attempt_words`.
- **Entry points:** the learner catalogue and attempt lifecycle, plus the admin
  authoring API.
- **Review workflow:** the same shape used by flashcard, quiz, and dictation,
  through its own status enum. Persistence types do not cross module boundaries,
  so the review columns are repeated here rather than borrowed.
- **Files:** recordings go from the browser straight to object storage through a
  presigned PUT bound to the declared size; nothing streams through the API.
- **AI:** enqueues through `ai`. It never calls a provider itself.

## `assessment`

Owns rubric-based productive practice for free Writing and Speaking responses.
Pronunciation read-aloud tasks remain in `speaking`; they measure a different
exercise and retain their existing percentage scores.

- **Tables:** `assessment_tasks`, `assessment_attempts`, `assessment_reviews`,
  `speaking_level_conversions`.
- **Level checks:** a `LEVEL_CHECK` task (Speaking only) is the spoken part of the
  roadmap's level check and stays out of the learner catalogue.
  `assessment.api.SpeakingLevelReader` turns a graded answer's four-criterion mean
  into a CEFR band using the conversion in force when it was graded.
- **Full test:** `assessment.api.AssessmentBandReader` gives the exam module's IELTS
  full test an answer's band (the four-criterion mean), when it was submitted, and a
  task's skill, type, certificate and whether it is published. Plain values only.
- **Roles:** staff author and grade their own tasks; administrators approve,
  reject or archive tasks and may correct a completed grade. Learners only read
  published tasks and their own drafts, submissions and reports.
- **Reporting:** `AssessmentQueueQuery` performs a declared read-only join to
  `users.display_name` for the authorized back-office search. Batched display-name
  lookup otherwise uses `user.api.UserDirectory`; no assessment code writes user rows.
- **Snapshots:** an attempt stores its task and rubric reference at creation;
  sample answers are hidden until completion. Published tasks cannot be edited.
- **Drafts:** Writing uses optimistic version checks, server persistence and a
  browser recovery copy. Submitted evidence cannot be edited.
- **Audio:** private recordings use size-bound upload tickets and are copied to
  an immutable submission key before grading; an old PUT ticket cannot replace
  the recording used for assessment.
- **AI:** `PRODUCTIVE_ASSESSMENT` jobs use the existing `ai` queue and provider
  boundary. Writing requests structured LLM feedback. Speaking adds transcript
  and acoustic evidence from Azure. All four rubric scores are validated; the
  server calculates the mean and labels the result an estimated practice score.
- **Recovery:** grading revision fences discard old results. A failed callback
  only changes an attempt when its current job actually failed. When automatic
  grading is disabled, submitted work goes directly to the human review queue.
- **Review:** human grading and administrator corrections save an audit row,
  including the previous report and the reviewer note.

## `tutor`

Owns the AI tutor conversation.

- **Tables:** `tutor_conversations`, `tutor_messages`, `tutor_requests`,
  `tutor_reply_versions`, `tutor_resources`, `tutor_reports`, `tutor_audit`,
  `tutor_recordings`, `tutor_deleted_requests`, `tutor_policy`, and `tutor_memories`.
- **Entry points:** `/api/tutor`, every route scoped to the caller; `/api/admin/tutor`
  for assigned report review and approved learning materials. Staff never receive
  an unfiltered learner conversation list.
- **Asking is synchronous, answering is not:** the question is stored and queued,
  and the screen polls the turn it created. A question is written before anything
  is asked of a provider, so a provider outage never loses what the learner typed.
- **Quota:** asks `ai` whether the learner has allowance left, and says no in its
  own words if not. It does not count its own work: the budget is shared with
  speaking, and only the queue sees both.
- **AI:** enqueues through `ai`. It never calls a provider itself.
- **Delivery:** durable account/request UUID receipts, revision and stream-epoch
  fences, immutable original answers with attached review corrections, and short
  database transactions. Explicitly edited questions create a new conversation.
- **Learning context:** reads minimal level/goal data through `user.api` and a
  selected SCORED quiz question through `quiz.api.SubmittedQuizContextReader`,
  completed Writing/Speaking through `assessment.api.SubmittedAssessmentContextReader`,
  and a scored exam question through `exam.api.SubmittedExamContextReader`.
  Each owner checks caller ownership and completion before exposing review data.
  Exam context includes bounded related passage/instructions, not private media URLs;
  productive context never exposes hidden criterion scores or reviewer-private fields.
  Profile and document text stay in untrusted context, never system instructions.
- **Knowledge:** Staff author/submit, Admin publish/withdraw; only published
  resource versions are retrieved. Historical citations retain their version.
- **Voice:** bounded private immutable WAV uploads; transcription uses the shared
  queue. A transcript never becomes a question without learner confirmation.
  Recordings expire after 24 hours. Cleanup and deletion intents run independently
  of the AI provider flag and never make provider calls.
- **Data:** owner-scoped paged JSON export; deleting Tutor data scrubs job content
  through the AI API, erases reports/revisions, and queues file cleanup. Non-content
  delivery tombstones last 30 days; daily allowance metadata is not refunded.
- **Memory:** learner-requested summaries use ordinary receipt/quota/worker delivery,
  bounded batches, versioned cursors and an epoch. Unverified notes stay in user
  context. Clear/review corrections fence late writes through a conversation memory
  advisory lock; correction races do not resurrect stale notes. Summaries are
  excluded from ordinary history input to avoid recursive compression.
- **Operations:** Admin metrics expose queue/end-to-end p95, terminal error codes
  and known/missing token usage without returning raw prompts or answers.
- **Release:** see `tutor-release-runbook.md`; memory, streaming and voice default off.

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

Provider parsing and failure handling have automated coverage. Real-provider
acceptance requires separately configured credentials and is not implied by those
tests. Productive practice supports a human review path when automatic grading is
disabled; see `productive-assessment-walkthrough.md` for setup and role workflows.

Frontend and mobile clients must call the Spring API, never FastAPI directly.

## Data pipeline boundary

`data_pipeline/` is an offline authoring and validation toolchain, not a runtime
Spring module. It owns generators, schemas, validators, local output, and QA reports.
Runtime ingestion must go through an explicitly owned backend use case; the pipeline
does not become a second writer to application tables. The flashcard module owns
`POST /api/admin/flashcards/import`; dictation owns
`POST /api/admin/dictation/import`. Each offers a dry run first, because the check
has to be visible before anything is saved, and each writes only into a draft -
generated content is not reviewed content, and an import that could reach a
published set would be a way around the review workflow. The cross-feature command
line adapter lives in `tooling.pipeline` and calls those service contracts; it owns
no business table.

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
