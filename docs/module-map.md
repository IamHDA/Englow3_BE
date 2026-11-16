# Module Map

This file records current ownership boundaries in the backend. Coding conventions
and package structure live in [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## `user`

Owns authentication-linked identity, learner profile, learning purposes, target
skills, and onboarding.

- **Tables:** `users`, `learner_profiles`, `learning_purposes`,
  `user_learning_purposes`, `user_target_skills`, `user_tour_completions`, `user_role_changes`.
- **Entry points:** profile, onboarding, and role-specific website tour APIs, and
  `GET /api/user/{userId}/public-profile` - display name, avatar, banner, whether the
  account is staff, and join date for the wall; never email, goals or scores. It knows
  nothing about blocks: `user` does not depend on `friendship`, so the page that shows the
  wall applies that rule.
- **Bootstrap read:** `/api/user/me/bootstrap` returns the caller's account and
  learner onboarding snapshot in one transaction. It does not create missing
  profiles, cache another account's data, or load learner state for Staff/Admin.
- **Authentication:** Supabase issues JWTs. The backend maps the token subject to
  `users.auth_provider_id`; application code does not manage passwords or sessions.
- **Role administration:** `/api/admin/users` is Admin-only. Role changes require the
  expected previous role and an audit reason; changing one's own role is refused.
  Privileged HTTP requests compare JWT authorities with the current database role;
  changed privileges are rejected with `ROLE_CHANGED` until sign-in refreshes the claim.
  Learner requests make no additional role lookup. `users.version` and dynamic updates
  prevent profile/onboarding writes from overwriting a concurrent role change.
- **Author scopes:** Staff see their private authored content and published shared
  material; only their own editable content can be changed. Admin sees all authors.
  The rule covers exams, quizzes, flashcards, dictation, pronunciation, translation,
  full tests and generation requests. Background generation names its actor explicitly.
- **Goals and measurements:** certificate goals require a valid target score; general
  goals require a higher CEFR level unless already C2. Completion and goal writes enforce
  the same requirements. Scoreless goal edits preserve an existing measured score;
  changing certificate clears scores from the previous scale. `PlacementRecorder` requires
  the original measurement time and distinguishes provisional/final IELTS callbacks.
  Older results cannot replace newer measurements, and provisional callbacks cannot
  replace a final result of the same sitting. Learner profiles use optimistic versions.
- **Account export:** declared read-only joins include full-test sittings, roadmap
  stages/items/milestones/adjustments, level checks, exam selections, quiz answers and
  pronunciation words. The export uses one repeatable-read snapshot and reports its
  per-kind row limit and possibly truncated sections in `exportMetadata`.
- **Cross-module reference:** `learner_profiles.placement_attempt_id` stores an exam
  attempt UUID. It is not a JPA relationship to the exam module.
- **Public API:** `user.api.UserDirectory` and `user.api.PlacementRecorder` are the
  contracts for cross-module identity lookup and placement recording.
  `user.api.PublicProfileReader` hands other modules what other learners may see of an
  account - display name and avatar URL, never email - for a set of ids in one query,
  whether an id is a learner, and the learner search by display name (`friendship`, `chat` and `blog` use it).
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
  reference data. IELTS is supplied by migration. Admin may append a complete TOEIC
  Listening/Reading reference through `/api/admin/exam-score-conversions/toeic` with
  source and effective date. Every correct count 0..100 must be covered exactly once
  per skill, with monotonic scores 5..495 in steps of five. Rows are inserted in one
  statement; existing dated tables and scored results are immutable. Full TOEIC papers
  use correct counts rather than weighted raw points for conversion and persist the
  L/R total in `exam_attempts.converted_score`; practice never receives a total.
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
  - **Availability:** sending for review, approval and starting recheck that each skill
    has 40 whole marks and usable band conversions for every score from 0 through 40.
    Grading deadlines use assessment's `assessedAt`, so an on-time grade still counts
    if a missed event is reconciled after the cutoff. A paper or task cannot be archived
    while referenced by a pending/published full test or an unsettled sitting, even if
    that full test has been archived. Publication, starting and archival share an
    advisory transaction lock. Assessment publishes `AssessmentTaskArchiving` synchronously
    before archival; exam may veto it without making assessment depend on exam.
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
    a final band, and `exam.api.CompletedFullTestReader` lets the roadmap read it.
  - **Why:** the full test must exist without a roadmap, and exam already owns mock
    papers and sittings. The grouping has no rules beyond combining bands, so a module
    of its own would only add wiring. `assessment` does not depend on `exam`, so the
    new dependency makes no cycle.
  - **Revisit if:** the full test gains rules of its own (retake policy, pricing, a
    review workflow unlike a paper's), or `assessment` ever needs to read exam data.

IELTS release hardening (V092–V094): `exam` additionally owns `full_test_component_attempts`
and `full_test_band_revisions`. Component attempts are created through
`FullTestComponentService`, under the owning sitting's lock, with a unique attempt claim.
`assessment.api.FullTestAssessmentStarter` creates/resumes assessment-owned drafts and
upload tickets; assessment never depends on exam. Standalone practice is not inferred into
new full tests. V094 recovers only unambiguous pre-deployment submissions.

Completed full tests may be amended after an administrator corrects an attached grade;
the original completion time and before/after band audit are retained. Profile updates
retain measurement-time ordering. Passed roadmap milestones retain their historical
achievement; they are not silently revoked or reused to pass another stage.
The sweep performs a declared read-only join to `assessment_attempts.version` to detect
missed corrections independently of clocks, and rotates all open sittings by last
reconciliation time. Assessment bands are batch-read through their owning API.
History is paged (default 20, maximum 100) with one batched full-test lookup.
Account export's declared reads include component bindings and band revisions scoped
through the owner's `full_test_sittings` rows.

Publication uses `ExamBlueprintQuery`/`ExamBlueprint` for complete IELTS/TOEIC mock
structure, required audio/passage references and section times. Uploaded asset bytes
remain validated by their upload owner. IELTS full-test availability rechecks the gate.
Academic is the supported full-test variant; General Training remains unavailable until
its content and conversion references are supplied. Flexible seven-day completion is
retained; the UI does not claim a continuous timed mock. Full-test Speaking Part 2 has
one minute of preparation and a two-minute recording limit. Assessment validates the
sealed PCM WAV's actual duration before enqueueing; ordinary practice retains rerecording.

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

Each sitting can pass at most one milestone of a roadmap, for both ordinary mock
papers and IELTS full tests. Settlement checks consumed result IDs under the roadmap
lock; timestamp equality never allows reuse. Explicit mock attachment also checks
the sitting's start time, rather than its later grading time.

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
- **IELTS mock milestones:** an IELTS roadmap's mocks are the exam module's full
  tests, passed on the overall band (D-IELTS-6), so `roadmap_milestones` names a
  `full_test_id` instead of an `exam_id` (V083). `RoadmapResultListener` hears
  `exam.api.FullTestCompleted`; progression reads the result through
  `exam.api.CompletedFullTestReader`. TOEIC milestones still name a paper.
- **Cross-module read:** `ContentCatalogQuery` reads published
  `flashcard_sets`, `dictation_lessons`, `quizzes`, `speaking_prompts`,
  `assessment_tasks`, `exams` and `full_tests`; `ItemProgressQuery` reads `flashcards`,
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
  `speaking_level_conversions`, `assessment_assignments`.
- **Level checks:** a `LEVEL_CHECK` task (Speaking only) is the spoken part of the
  roadmap's level check and stays out of the learner catalogue.
  `assessment.api.SpeakingLevelReader` turns a graded answer's four-criterion mean
  into a CEFR band using the conversion in force when it was graded.
- **Task 1 charts:** a Writing Task 1 may carry a chart (`image_object_key`, V084) in
  the learning bucket, uploaded by its author while it is editable. `helper.TaskImages`
  checks the bytes, stores it, and signs a fresh URL whenever a task is shown; a
  snapshot keeps the key only. The figures the grader reads stay in `rubric_notes`.
- **Full test:** `assessment.api.AssessmentBandReader` gives the exam module's IELTS
  full test an answer's band (the four-criterion mean), when it was submitted, and a
  task's skill, type, certificate and whether it is published. Plain values only.
- **Roles:** staff author their own tasks. Admin can assign a waiting/failed submission
  to another Staff/Admin reviewer with an expected submission version and audit reason.
  The assigned reviewer has exclusive Staff grading access; an unassigned submission
  falls back to its task author. Nobody may grade their own submission. Queue lists,
  workload counts and detail/grade authorization use the same rule. Read-only submission,
  task and review-history access takes no write lock; assignment/grading writes still do.
  Administrators approve, reject or archive tasks and may correct a completed grade. Learners only read
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

## `generation`

GenerationArtifacts commits the paper, five task drafts and the assemble checkpoint in one
short database transaction under a generation-request row lock. The finish step likewise
commits all review submissions, the full-test draft and its checkpoint together. A repeated
job observes the advanced checkpoint and creates nothing. Job completion and terminal
failure callbacks check both step and attempt; a late callback cannot fail newer work.
Provider calls and media uploads remain outside those transactions.

Owns AI content generation requests: an ADMIN or STAFF member presses "generate",
the server writes a whole set with a model, and the result lands as drafts in the
modules that own that content. Today the only kind is `IELTS_FULL_TEST`; the name is
general because TOEIC papers, flashcards or quizzes are the next likely kinds.

- **Subdomain:** supporting. It speeds up authoring; the product wins on the content
  and the review behind it, which stay where they are.
- **Owns writes to:** `generation_requests` - who asked, the kind, an optional
  author's brief (free text, at most 1000 characters, steering topics, difficulty and
  style), status, the step running and its attempt count, the components written so
  far, the ids it produced and the failure reason.
- **Owns no content:** the paper and full test stay in `exam`, the five Writing and
  Speaking tasks in `assessment`. It creates them through their APIs as drafts
  authored by the requester, sends the paper and tasks for review and builds a draft
  full test, and keeps only their ids. Nothing it makes reaches learners without an
  administrator approving it.
- **Entity with rules:** `GenerationRequest` - status only moves forward (`QUEUED ->
  RUNNING -> COMPLETED | FAILED`); a step is tried at most three times, then the whole
  request fails with its reason. Version column: the worker and the requester can
  touch one row.
- **Rules across rows (service, under a per-user lock):** at most three requests a day
  per user, counting queued, running and completed ones - a failed request does not
  count (decided 2026-11-07, accepting that repeated failures have no cost ceiling);
  one request in progress per user at a time.
- **Talks to:** `ai` by enqueueing one job per step (`CONTENT_GENERATION`, outside the
  learners' daily allowance); `exam` and `assessment` through module APIs taking an
  explicit author id, because the worker has no signed-in user; `ai_service` over the
  internal HTTP contract.
- **Steps:** plan topics -> each Listening part, Reading passage, Writing Task 1,
  Task 2 and Speaking test (a rule failure retries that step with the problems fed
  back) -> assemble (paper, tasks, Task 1 chart as drafts) -> record each Listening
  part -> finish (submit for review, draft full test). One step per job keeps every
  job under the worker's run limit.
- **Prompts and rules:** live once in `ai_service` (moved from
  `data_pipeline/ielts`); the offline command-line generator reuses them. The model is
  configured on its own (`AI_SERVICE_GENERATION_*`), apart from grading and the tutor.
  The author's brief is added to every prompt; the structural rules still apply, so a
  brief cannot produce a paper without its 40 + 40 marks and question types.
- **Difficulty:** each part is written to a stated level, rising through the paper
  (Listening Part 1 at B1-B2 up to Part 4 at C1-C2; Reading Passage 1 at upper B2 up to
  Passage 3 at C1-C2), and the rules reject what makes a paper easier than the real one,
  checked by string matching against the text rather than by the model's word for it:
  every question quotes the `evidence` that settles it; a correct option, a statement
  or the notes beside a gap may not repeat the text word for word; a recording runs
  550-1100 words, answers its questions in order, and four questions in ten carry a
  `trap` - the tempting wrong words, quoted from the recording. Evidence and trap
  become each question's explanation, shown after marking. Recordings are made at the
  voice's own speed. The level of a question stays a prior (`EASY`/`MEDIUM`/`HARD` by
  part) until candidates' answers measure it.
- **Why:** a generation run has its own lifecycle and language (request, step,
  attempt, brief) that is neither a paper's nor a task's, and more kinds of generated
  content are expected.
- **Revisit if:** generation stays IELTS-only for long (fold it into `exam`), or failed
  runs start costing real money (add a ceiling on failed requests).
- **Left open:** cancelling a running request; regenerating one part of a finished
  set; similarity checks between passages of different sets.

## `friendship`

Implemented: friend requests, friendships, blocks and the learner search, behind
`/api/friends`. Owns who is connected to whom among learners.

- **Subdomain:** supporting. It keeps learners coming back; the product wins on
  practice and AI feedback, not on the social graph.
- **Owns writes to:** `friendships` (`id`, `requester_id`, `addressee_id`, `status`
  `PENDING` or `ACCEPTED`, the request/accept times) and `user_blocks` (`blocker_id`,
  `blocked_id`). Declining, cancelling and unfriending delete the row; no request
  history is kept. Columns are named for the roles rather than for a sorting trick.
- **Who:** learners only. Staff and Admin neither send nor receive requests, checked
  through `PublicProfileReader.isLearner` (a role of exactly `LEARNER`), not
  `UserDirectory.isBackOfficeUser`. Sending, accepting, blocking and searching require the
  caller to be a learner; withdrawing, unfriending, unblocking and reading one's own lists
  do not, so an account whose role changed can still clean up what it already has. The
  relationship and friend-count reads never refuse on role: they answer `NOT_APPLICABLE`
  or a count, so a wall can always ask.
- **Entities with rules:** `Friendship` - nobody befriends themselves, only the
  addressee accepts, only a pending request can be accepted. A request from B while
  A's request to B is pending accepts it rather than creating a second row. Blocks are
  plain rows; their rule spans both tables and lives in the service: blocking deletes
  the pair's friendship or pending request, and no request is created or accepted
  while a block exists either way.
- **Concurrency:** a unique index on
  `(least(requester_id, addressee_id), greatest(requester_id, addressee_id))` makes a
  second row for one pair impossible, whichever side asked.
  Every write for a pair (request, accept, block, unfriend) takes a transaction
  advisory lock on the pair, so a block cannot race an accept.
- **Talks to:** `user` via `user.api.PublicProfileReader` (name and picture for a whole
  page of people in one call, whether an id is a learner, and the learner search by
  display name - never by email) and `UserDirectory` (the caller's id). Publishes
  `friendship.api.FriendshipReader` - are two learners friends, is either blocked,
  which ids has a learner blocked or been blocked by - for `chat` and `blog`. It
  depends on neither of them.
- **Erasure:** both tables cascade from `users`.
- **Read path:** repository only; the search itself runs in `user`, behind
  `PublicProfileReader`, because it reads `users`.
- **Entry points:** `/api/friends` (friends, requests, search, relationships, and
  `/users/{userId}/count` for a learner's friend count) and `/api/friends/blocks`.
- **Relationship as seen by a viewer:** `NONE`, `FRIENDS`, `REQUEST_SENT`,
  `REQUEST_RECEIVED`, `BLOCKED_BY_ME`, `UNAVAILABLE` (the other side blocked the viewer;
  the reason is never stated), `SELF`, and `NOT_APPLICABLE` (viewer or target is not a
  learner, so no friend actions apply).
- **Visibility:** anyone may read how many friends a learner has; only the owner reads
  the list itself. Both are refused as not found when the two block each other.
- **Wall (a screen, not a module):** each account's page is composed in the BFF from
  three independent calls made in parallel - the public profile (`user`), the
  relationship and friend count (`friendship`), and posts by author (`blog`). Staff and
  Admin have walls with posts and no friend actions. When the relationship says
  `UNAVAILABLE` or `BLOCKED_BY_ME`, the BFF shows the wall as unavailable; the posts
  themselves are already withheld by `blog`, so the only thing the BFF rule hides on its
  own is the public name and picture.
- **Why:** request, accept and block are a different vocabulary and lifecycle from a
  conversation's, and `blog` needs blocks without needing chat.
- **Revisit if:** group chat or following without friendship arrives, or blocks need a
  reason and a report trail.

## `chat`

Implemented: one-to-one conversations between friends - open a conversation, send text
and/or one image, history by `seq`, read marks, recall, the conversation list with unread
counts - over REST behind `/api/chat`, and a receive-only WebSocket at `/ws/chat` that pushes
new messages, recalls, read marks and typing. Owns one-to-one conversations between friends.

- **Subdomain:** supporting.
- **Owns writes to:** `chat_conversations` (`id`, `direct_key`, `last_seq`,
  `last_message_at`); `chat_participants` (`conversation_id`, `user_id`,
  `last_read_seq`, `joined_at`); `chat_messages` (`id`, `seq` within the
  conversation, `sender_id`, `client_id`, text, an optional image object key,
  `recalled_at`, `sent_at`); `chat_image_uploads` (`id`, `conversation_id`, `uploader_id`,
  `object_key`, `content_type`, `content_length`, `created_at`: an image a participant was
  given an upload URL for and has not yet attached to a message - sending consumes the row,
  and without it a learner could upload any number of images that are never sent and no job
  could find them); `chat_socket_tickets` (`token_hash`, `user_id`, `expires_at`,
  `used_at`, `created_at`: the single-use tickets a browser opens the socket with; only the
  SHA-256 of a ticket is stored). A conversation holds no user columns: who is in it, and
  how far each has read, are participant rows. Sending moves the sender's own read mark, so
  unread is `last_seq - last_read_seq` without counting rows.
- **One conversation per pair:** `direct_key` is `"<smaller uuid>:<larger uuid>"`,
  unique, so two friends opening a chat at the same moment get one conversation. It
  is the only place the pair is sorted.
- **Entities with rules:** `ChatConversation` hands out the next `seq`.
  `ChatMessage` - text (at most 4000 characters), one image, or both, never empty; only
  the sender recalls it, once; a recalled message loses its text and image and shows as
  recalled; no editing. `ChatParticipant` is plain: the read mark has no setter and is
  written only by `greatest(...)` in `ChatParticipantRepository.advanceReadMark`, which
  keeps it from moving backwards or past `last_seq` even when two tabs write at once.
  `ChatImageUpload` is a plain row.
- **Who may send:** the two must be friends with no block either way, asked through
  `FriendshipReader` on every send and never cached, so unfriending or blocking stops
  the next message. History stays readable after unfriending.
- **Concurrency:** a send takes the conversation row `FOR UPDATE`
  (`@Lock(PESSIMISTIC_WRITE)`) and `ChatConversation.nextSeq` increments `last_seq` in
  the entity; the lock is held to commit, so `seq` order is commit order. This replaces
  the single `UPDATE ... RETURNING` the first design named: the guarantee is the same,
  but the rule now lives in the entity and is tested without a database. Unique
  `(conversation_id, seq)` backs it. Unique `(sender_id, client_id)` makes a retried
  send return the first message (checked before and again under the lock). The read mark
  is written with `greatest(...)`. A recall takes the message row `FOR UPDATE`, so two
  recalls at once take turns and the second is refused as already recalled. Two friends
  opening a chat at once meet in `insert ... on conflict (direct_key) do nothing`.
- **Delivery:** sending, reading, recalling and history are REST through the
  existing proxy and BFF. Receiving is a receive-only WebSocket at `/ws/chat`, straight
  to Spring. The HTTP layer lets the handshake through (`permitAll` - the only route
  opened for it) and `ChatSocketHandshakeInterceptor` decides who the socket belongs to.
  The web asks the proxy (`POST /api/chat/socket-tickets`) for a ticket - random, stored
  hashed, valid 30 seconds, single use, redeemed by one conditional `UPDATE` so it works on
  any instance; mobile may send `Authorization: Bearer` instead. Neither is a 401. The
  handshake checks `Origin` against `app.cors.allowed-origins` (a 403). The only client
  frame is `{conversationId, active}` for typing, which is relayed to the other person
  while the two are still friends and never stored; any other frame, broken or unknown, is
  dropped without closing the socket. Pushes happen after commit (`AFTER_COMMIT`
  listeners; a failure is logged and never fails the request that committed): a new
  message, a recall and a read mark go to the other person and to the actor's own other
  sockets, to participants only. Sockets are held in memory - five per person at most, the
  oldest closed with a policy violation past that - each behind a decorator that
  serialises sends, with a ping every 25 seconds so an idle chat is not cut by a proxy.
  After every (re)connect the client catches up over REST: the
  conversation list carries each conversation's `last_seq`, and an open conversation
  fetches messages after its own `seq`. There is deliberately no single cursor across
  conversations: an id or sequence is handed out before commit, so a later one can
  commit first and a reconnect would skip the earlier message. Within one
  conversation `seq` is safe, because the `last_seq` row lock is held to commit.
- **Images:** presigned PUT through the shared storage client into the private `chat`
  bucket (`app.storage.chat-bucket`), bound to the declared type (JPEG, PNG, WebP) and
  size (at most 5 MB), keyed `conversations/{conversationId}/{uploadId}.{ext}`; read
  through short-lived presigned GETs issued only to the two participants. Object-key
  meaning stays in `chat`. Sending names the upload id: the object's existence is checked
  with a HEAD outside any transaction, then the upload row is consumed in the write
  transaction. Recalling deletes the object after commit. A cleanup job
  (`app.chat.cleanup-enabled`, hourly) removes uploads nobody sent within a day, with
  their objects, conversations nobody is in, and socket tickets that expired long ago; every removal is a conditional delete
  (the conversation delete re-checks that nobody is in it and says which it removed), and
  only the sweep whose delete took effect removes the objects, so it is safe on several
  instances. A sweep that fails is logged and does not stop the others.
- **Erasure:** messages stay and show as from a deleted user: `sender_id` is set to
  null and the erased learner's participant row cascades. A direct conversation left
  with one participant shows the other side as a deleted user; one left with none is
  cleaned up. Images the erased learner sent stay with their messages. An upload URL
  issued but never used goes with the account (its row cascades); if the file had already
  been PUT it stays in the bucket as a stray. The privacy policy has to say so.
- **Moderation:** none in this version. No reporting; a learner protects themselves by
  blocking, and staff never read private messages.
- **Talks to:** `friendship` via `FriendshipReader`; `user` via `UserDirectory` and
  `PublicProfileReader` (the other person's name and picture for a whole page of
  conversations in one call).
- **Entry points:** `/api/chat` - `conversations` (list, open, one, `messages` history and
  send, `read`, `image-uploads`), `messages/{id}/recall`, `unread-count` (conversations
  with something unread, not a message count) and `POST /api/chat/socket-tickets`; and the
  WebSocket `/ws/chat`.
- **Read path:** repository for everything but the conversation list; the list (last
  message, unread, newest first, only conversations that have a message) is
  `chat/query` because it is one join across three tables.
- **Why WebSocket, receive-only:** web and BFF run as Vercel functions - 60 seconds at
  most, no WebSocket - and the browser never holds the token (HttpOnly session
  cookie). SSE through them would keep two functions running for every learner with
  the app open and cannot carry typing cheaply. Sends stay on REST so a dropped socket
  never loses a message, and validation, errors and rate limits stay the existing ones.
- **Where the web listens:** the socket is opened only on the chat screens. Everywhere
  else the header shows a new-message indicator fed by `GET /api/chat/unread-count`,
  asked again on navigation, on returning to the tab and about once a minute - so a
  learner who is not chatting holds no connection to the backend.
- **Revisit if:** group chat (participant rows already allow it; `direct_key` becomes
  nullable for groups), presence, or send latency starts to matter (send over the
  socket too); abuse in chat is reported (a report flow that shows staff only the
  reported message); the backend runs more than one instance (see Still open).

## `blog` (moderation pending)

Implemented: posts with Markdown, images and tags, flat comments, likes and the post list,
behind `/api/blog`. Reports and moderation (`/api/admin/blog`) are not built yet; the
`status` columns exist and every read and write already respects them. Owns knowledge-sharing
posts, their comments, likes, reports and moderation.

- **Subdomain:** supporting.
- **Owns writes to:** `blog_posts` (title, Markdown body, author, status, version),
  `blog_post_tags`, `blog_post_images`, `blog_comments`, `blog_post_likes`. Comments are
  flat - no replies. `blog_reports` and `blog_moderation_actions` are created with
  moderation, not before.
- **Who:** every signed-in account posts, comments and likes; nothing is readable
  without signing in (no route under `/api/blog` is public). Staff and Admin moderate through
  `/api/admin/blog`, a separate controller in this module - never content they wrote themselves.
- **Publishing:** a post is visible as soon as it is posted; moderation follows
  reports. Moderators hide (reversible) or remove (permanent) a post or comment, or
  dismiss its reports, always with a reason, recorded in `blog_moderation_actions`. Until
  moderation exists nothing sets `HIDDEN` or `REMOVED`; the code only reads them.
- **What an author sees:** their own posts and comments in every status, with the
  status shown, so a hidden or removed post is found in their own list rather than
  only by its link. Everyone else sees published posts and visible comments. In the API this
  is `GET /api/blog/posts?authorId=<the caller>`: the one list that includes every status
  (the query decides it from `authorId == viewer`, so no other filter combination can reach
  hidden posts), and the comment list returns the caller's own hidden or removed comments
  with their `status`. A hidden or removed post is opened only by its author, and then
  `canEdit` and `canInteract` are false.
- **One read rule:** a post that does not exist, is hidden from the caller, or is by someone
  the caller has a block with is the same `BLOG_POST_NOT_FOUND`, so neither moderation state
  nor a block is revealed. It applies to reading, editing, deleting (someone else's post the caller
  cannot read is `BLOG_POST_NOT_FOUND`, not a refusal that would show it exists), listing and writing
  comments, editing a comment and liking; unliking, deleting one's own post and deleting one's own
  comment are allowed in every status (an unlike of a post the caller cannot read reports a like count
  of 0). One package-private collaborator, `BlogReadRule`, answers the question for every service.
- **Tags:** skill, level and certificate, as blog's own enums - never `exam`'s or
  `user`'s - used for filtering (a filter that is given must match; tags combine with AND).
  Attaching a specific exam or deck is not in this version.
- **Entities with rules:** `BlogPost` - only the author edits or deletes it; a hidden
  or removed post cannot be edited by its author or commented on; title 1-200 characters,
  body 1-20 000, at most 8 tags. `BlogComment` - the same author rule, 1-2000 characters, no
  edit once hidden or removed. `BlogTag` is an immutable embeddable checked against the
  kind's own enum. `BlogReport` (with moderation) - one open report per reporter and target,
  resolved once; after it is closed the same reader may report that content again,
  because an edit can make dismissed content a problem later. Likes and tags are
  plain rows; images are plain rows whose `post_id` is written only by a conditional update.
- **Blocks:** posts and comments by anyone blocked either way are left out of a
  learner's lists, and neither side can comment on, like or open the other's posts, via
  `FriendshipReader`. Blocking is learner-only, so for Staff and Admin the blocked set is
  normally empty. A block never hides a person's own posts from their own list. A post whose
  author is erased (null) is nobody's to hide and stays in every list, whatever blocks the viewer has.
- **Concurrency:** `@Version` on `blog_posts`, so an author's edit cannot silently
  undo a moderator's hide: the editor sends back the version it opened and a post that has
  changed since is refused as `BLOG_POST_STALE`; Hibernate's check on the write turns two saves that
  both passed it into `CONCURRENT_UPDATE`. Like primary key `(post_id, user_id)` with an
  `on conflict do nothing` insert. Like and comment counts are counted for each page of posts,
  not stored: erasure removes likes by cascade, and a stored counter would drift. Unique
  `(reporter_id, target_type, target_id)` among open reports only (with moderation).
- **Images:** the Markdown refers to an image as `blog-image:<id>`, never by URL, so the
  `blog` bucket stays private, a link that expires cannot break a saved post, and moving the
  storage domain changes no data. The read API returns the body as written plus
  `images: [{id, url}]` with presigned GETs (`app.storage.blog-image-url-ttl`, 3 hours) and the
  page swaps each reference for its URL. An upload is a presigned PUT
  (`POST /api/blog/images`, bound to the declared type - JPEG, PNG, WebP - and size - at most
  5 MB - and valid 10 minutes) into `app.storage.blog-bucket`, keyed `posts/{uploaderId}/{imageId}.{ext}`;
  the row is written with `post_id` null. At most 10 images per post. Saving a post names
  its images in the text: each must exist, be the caller's and be unattached or already this
  post's; the object's existence is checked with a HEAD outside any transaction; and the write
  attaches them with a conditional update whose row count must equal the number it expected, so a
  post is never saved pointing at an image it did not get (another post took it, or the sweep
  removed it) - that rolls the write back. An image taken out of the text on edit is deleted
  (row, then object after commit). Deleting a post removes its image rows by cascade but not their
  objects, so every path that deletes a post reads the keys first and deletes the objects after
  the commit. A cleanup job (`app.blog.cleanup-enabled`, hourly) removes images no post took
  within a day, with their objects, by conditional delete.
- **Excerpt:** the list reads the first 1200 characters of each body and `BlogMarkdown.excerpt`
  makes a plain-text excerpt of at most 200 characters (the ellipsis counts) at a word boundary.
  Text that ends inside an image or a link never reaches the excerpt, so no truncated
  `blog-image:` fragment leaks.
- **Erasure:** posts and comments stay and show as by a deleted user (`author_id`
  set to null) - confirmed by the product owner on 2026-11-16, the same choice as
  chat; likes cascade; images stay with their post (an upload nobody used keeps a null
  `uploader_id` and is swept after a day); the erased user's reports are deleted (with
  moderation). The privacy policy has to say so.
- **Talks to:** `friendship` via `FriendshipReader` (the blocked set for a list, whether two
  accounts are blocked for one post); `user` via `UserDirectory` (the caller's id) and
  `PublicProfileReader` (name and picture for a whole page of authors in one call).
- **Entry points:** `/api/blog` - `posts` (list, create, one, edit, delete), `posts/{id}/like`,
  `posts/{id}/comments`, `comments/{id}`, `images` and `tags`.
- **Read path:** the post list - optional tag, title, author and sort filters with paging -
  lives in `blog/query/BlogPostListQuery`, read-only, `JdbcClient`, every value bound and no empty
  collection ever given to `in`; everything else is repository. The comment list is a repository
  query that always passes at least one id to `not in`.
- **Why:** posts, comments and moderation have their own lifecycle and reviewers;
  nothing here shares tables or vocabulary with the learning modules.
- **Revisit if:** learners need search beyond tags and title, attaching learning
  content, pre-moderation for new accounts, or saved posts. Sorting by likes is slow - it
  counts the likes of every matching post on each call: store a reconciled counter or a
  statistics table. The comment count on a post card counts every visible comment whatever the
  viewer has blocked, so a block can leave it a little high; make it per viewer only if that is
  noticed.

## `shared` and `config`

These are technical packages, not business modules, and own no business tables.

- `shared/error` owns the common error response and HTTP exception translation.
- `shared/security` exposes authenticated token information without owning user
  roles or profile data.
- `shared/storage` provides generic object-storage operations without business key
  conventions.
- `shared/page` and `shared/logging` contain transport-level infrastructure.
- `config` wires security, OpenAPI, storage clients, and the chat WebSocket registration.

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
- **Chat across instances.** A push reaches only sockets on the instance that
  committed the message. Before the backend runs two instances, chat needs Postgres
  `LISTEN/NOTIFY` (or equivalent) between them. `LISTEN` holds a dedicated connection,
  which the session-mode pooler allows and transaction mode does not.
- **Chat socket sends run on shared threads.** A push runs on the request thread that
  committed the change and a ping on Boot's single scheduler thread; a half-open socket
  can block either for Tomcat's blocking-send timeout. Move push and ping to a dedicated
  executor (or lower the timeout) before chat traffic grows.
- **Notifications** for friend requests, new messages while offline and comments on
  one's posts. No module owns notifications yet; decide when the first one is built.
- **Account export** does not yet cover `friendship`, `chat` or `blog`.
- **Privacy policy text** for erasure: chat messages, their images, blog posts and
  comments stay after an account is erased, shown as by a deleted user. Decided; the
  policy page still has to state it.
