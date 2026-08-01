# Implementation Patterns

Skeletons, not templates. Match the conventions already in the module over the shapes here.

## Contents

1. A command slice
2. A plain CRUD slice
3. Queries and projections
4. Complex read-only queries
5. Calling another module
6. Breaking a dependency cycle with events
7. Async jobs
8. Concurrency
9. Error handling
10. Tests

## A command slice

Use when the entity has a rule to protect.

**Entity** - rules inside, no setters for guarded fields:

```java
@Entity
@Table(name = "exams")
@Getter                                  // no @Setter, no @Data
public class Exam {

    @Id
    private UUID id;
    private String title;

    @Enumerated(EnumType.STRING)
    private ExamStatus status;

    private UUID reviewedBy;
    private Instant publishedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();

    protected Exam() { }                 // required by JPA

    public static Exam draft(String title) {
        Exam exam = new Exam();
        exam.id = UUID.randomUUID();     // identity assigned in code
        exam.title = title;
        exam.status = ExamStatus.DRAFT;
        return exam;
    }

    public void publish(UUID reviewerId, Instant now) {
        if (status != ExamStatus.PENDING_REVIEW) {
            throw new InvalidExamStatusException(id, status);
        }
        if (questions.isEmpty()) {
            throw new EmptyExamException(id);
        }
        this.status = ExamStatus.PUBLISHED;
        this.reviewedBy = reviewerId;
        this.publishedAt = now;
    }
}
```

**Service** - transaction, orchestration, mapping:

```java
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository exams;
    private final CurrentUser currentUser;

    @Transactional
    public ExamResponse publish(UUID examId) {
        Exam exam = exams.findById(examId)
                .orElseThrow(() -> new ExamNotFoundException(examId));

        exam.publish(currentUser.requireId(), Instant.now());

        return ExamResponse.from(exam);   // still inside the transaction
    }
}
```

No `save()` call: the entity is managed, so the change flushes at commit. Calling `save()` on an already-managed entity is harmless but adds nothing.

**Controller** - HTTP only:

```java
@RestController
@RequestMapping("/api/admin/exams")
@RequiredArgsConstructor
class AdminExamController {

    private final ExamService examService;

    @PostMapping("/{id}/publish")
    ExamResponse publish(@PathVariable UUID id) {
        return examService.publish(id);
    }
}
```

**Response record** - mapping as a static factory, no mapper class:

```java
public record ExamResponse(UUID id, String title, String status, Instant publishedAt) {
    public static ExamResponse from(Exam exam) {
        return new ExamResponse(exam.getId(), exam.getTitle(),
                                exam.getStatus().name(), exam.getPublishedAt());
    }
}
```

## A plain CRUD slice

No invariants, so no entity behavior. Do not invent rules to justify a richer shape.

```java
@Service
@RequiredArgsConstructor
public class ExamCategoryService {

    private final ExamCategoryRepository categories;

    @Transactional
    public CategoryResponse rename(UUID id, String name) {
        ExamCategory category = categories.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
        category.setName(name);           // setters are fine here
        return CategoryResponse.from(category);
    }
}
```

## Queries and projections

Prefer a record projection over loading entities for read-only endpoints:

```java
public interface ExamRepository extends JpaRepository<Exam, UUID> {

    @Query("""
        select new com.app.exam.dto.ExamListItem(e.id, e.title, e.status, e.publishedAt)
        from Exam e
        where (:status is null or e.status = :status)
        """)
    Page<ExamListItem> search(@Param("status") ExamStatus status, Pageable pageable);
}
```

This handles one or two optional filters. Past that, the `null` checks multiply and the query plan degrades - move to `query/`.

## Complex read-only queries

Use `query/` for optional filter combinations, aggregation, or joins across modules. Read-only, always.

```java
@Repository
@RequiredArgsConstructor
public class ExamStatsQuery {

    private final DSLContext dsl;

    public List<LevelStat> countByLevel(LocalDate from, LocalDate to, Set<String> levels) {
        Condition where = EXAM_ATTEMPTS.SUBMITTED_AT.between(from.atStartOfDay(), to.atStartOfDay());
        if (!levels.isEmpty()) {
            where = where.and(EXAM_ATTEMPTS.LEVEL.in(levels));   // filters compose
        }

        return dsl.select(EXAM_ATTEMPTS.LEVEL, count())
                  .from(EXAM_ATTEMPTS)
                  .where(where)
                  .groupBy(EXAM_ATTEMPTS.LEVEL)
                  .fetch(r -> new LevelStat(r.value1(), r.value2()));
    }
}
```

Composing conditions like this is the actual reason to use a query DSL. If every filter is mandatory, plain SQL is simpler.

Regenerate the query metadata after any migration. A stale reference fails at runtime, which defeats the purpose.

## Calling another module

Ask the owning module; never touch its tables.

```java
@Transactional(readOnly = true)
public List<AttemptResponse> listAttempts(UUID examId) {
    List<ExamAttempt> attempts = attempts.findByExamId(examId);

    Set<UUID> learnerIds = attempts.stream()
            .map(ExamAttempt::getLearnerId)
            .collect(toSet());

    Map<UUID, LearnerSummary> learners = learnerService.findSummaries(learnerIds);  // one call

    return attempts.stream()
            .map(a -> AttemptResponse.from(a, learners.get(a.getLearnerId())))
            .toList();
}
```

`LearnerSummary` is a record owned by the learner module. Its entities stay inside it. Fetch by the whole ID set - a lookup per row is the same mistake as an N+1 query, one layer up.

For a state change in another module, call its service and let it own the write:

```java
@Transactional
public void submit(UUID attemptId) {
    ExamAttempt attempt = attempts.findById(attemptId).orElseThrow();
    attempt.submit(Instant.now());

    progressService.recordActivity(attempt.getLearnerId(), Instant.now());
}
```

## Breaking a dependency cycle with events

If two modules would call each other, check the direction first - usually only one needs to know. When both genuinely do, publish and listen:

```java
// exam module - publishes, knows nothing about listeners
events.publishEvent(new ExamSubmittedEvent(attempt.getId(), attempt.getLearnerId(), now));

// progress module - listens, runs after the publisher's transaction commits
@TransactionalEventListener
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void on(ExamSubmittedEvent event) {
    streaks.record(event.learnerId(), event.at());
}
```

In-process events are not durable. If the reaction must not be lost, persist it as a job instead.

## Async jobs

Persist, commit, then enqueue. Enqueueing inside an open transaction lets a worker consume the message before the row is visible.

```java
@Transactional
public UUID requestAnalysis(UUID submissionId) {
    AiJob job = AiJob.pending(submissionId);
    jobs.save(job);

    // after commit, so the worker never sees a missing row
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override public void afterCommit() { queue.enqueue(job.getId()); }
        });

    return job.getId();
}
```

Worker side: check state before acting so redelivery is harmless, bound the retries, and make failure terminal and visible.

```java
public void handle(UUID jobId) {
    AiJob job = jobs.findById(jobId).orElseThrow();
    if (!job.isPending()) {
        return;                                  // already handled - idempotent
    }
    job.markRunning(Instant.now());
    jobs.saveAndFlush(job);
    try {
        job.complete(provider.analyse(job.getSubmissionId()), Instant.now());
    } catch (ProviderException e) {
        job.fail(e.getMessage(), Instant.now()); // bounded retry inside fail()
    }
}
```

A job must never end in a state nothing will pick up again. Time out stuck work and surface dead jobs.

## Concurrency

Entity rules do not stop two concurrent transactions from both passing the same check. Add the mechanism that fits:

```java
@Version
private long version;                            // lost-update protection
```

```java
@ExceptionHandler(OptimisticLockingFailureException.class)
ResponseEntity<ErrorResponse> onConflict(OptimisticLockingFailureException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("CONCURRENT_UPDATE"));
}
```

```sql
-- the only real guarantee for "once per learner per exam"
create unique index uq_attempt_learner_exam on exam_attempts (learner_id, exam_id);
```

```java
// counters: atomic update instead of read-modify-write
@Modifying
@Query("update LearningProgress p set p.completed = p.completed + 1 where p.id = :id")
void incrementCompleted(@Param("id") UUID id);
```

## Error handling

Module exceptions carry business meaning; one advice translates them to HTTP.

```java
public class InvalidExamStatusException extends DomainException {
    public InvalidExamStatusException(UUID examId, ExamStatus actual) {
        super("EXAM_INVALID_STATUS", "Exam %s is %s".formatted(examId, actual));
    }
}
```

Do not throw `ResponseStatusException` from a service - that puts HTTP concerns into the wrong layer. Keep the status mapping in the advice.

## Tests

- **Entity tests** - construct the entity directly, no Spring, no database. This is where the rules are worth testing.
- **Service tests** - mock repositories and other modules' services; assert orchestration and the paths that throw.
- **Controller tests** - `@WebMvcTest` with a mocked service; assert status codes and response shape.
- **Integration tests** - `@SpringBootTest` against a real database for migrations, constraints, and concurrency behaviour.

Test the rules and the failure paths. Getter round-trips and framework behaviour are not worth the maintenance.
