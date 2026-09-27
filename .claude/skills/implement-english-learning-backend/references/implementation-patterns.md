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

**Service** - transaction, orchestration, mapping to a result:

```java
public interface ExamService {
    ExamResult publish(PublishExamCommand command);
}

@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final ExamRepository exams;
    private final CurrentUser currentUser;

    @Transactional
    public ExamResult publish(PublishExamCommand command) {
        Exam exam = exams.findById(command.examId())
                .orElseThrow(() -> new ExamNotFoundException(command.examId()));

        exam.publish(currentUser.requireId(), Instant.now());

        return ExamResult.from(exam);   // still inside the transaction
    }
}
```

No `save()` call: the entity is managed, so the change flushes at commit. Calling `save()` on an already-managed entity is harmless but adds nothing.

**Controller** - HTTP only, maps request to command and result to response:

```java
@RestController
@RequestMapping("/api/admin/exams")
@RequiredArgsConstructor
class AdminExamController {

    private final ExamService examService;

    @PostMapping("/{id}/publish")
    ResponseEntity<ExamResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ExamResponse.from(examService.publish(new PublishExamCommand(id))));
    }
}
```

A command with a single field that already comes from a `@PathVariable` (nothing to destructure from a request body) is still worth it once the service needs to stay decoupled from `@PathVariable`/`@RequestBody` - skip it only for use cases with no input at all.

**Result and response records** - mapping as static factories, no mapper class. A result is service output; it may already contain a resolved URL when the service owns storage policy, while a response record is the HTTP shape:

```java
public record ExamResult(UUID id, String title, ExamStatus status, Instant publishedAt) {
    public static ExamResult from(Exam exam) {
        return new ExamResult(exam.getId(), exam.getTitle(), exam.getStatus(), exam.getPublishedAt());
    }
}

public record ExamResponse(UUID id, String title, String status, Instant publishedAt) {
    public static ExamResponse from(ExamResult result) {
        return new ExamResponse(result.id(), result.title(), result.status().name(), result.publishedAt());
    }
}
```

## A plain CRUD slice

No invariants, so no entity behavior. Do not invent rules to justify a richer shape.

```java
public interface ExamCategoryService {
    CategoryResult rename(RenameCategoryCommand command);
}

@Service
@RequiredArgsConstructor
public class ExamCategoryServiceImpl implements ExamCategoryService {

    private final ExamCategoryRepository categories;

    @Transactional
    public CategoryResult rename(RenameCategoryCommand command) {
        ExamCategory category = categories.findById(command.id())
                .orElseThrow(() -> new CategoryNotFoundException(command.id()));
        category.setName(command.name());   // setters are fine here
        return CategoryResult.from(category);
    }
}
```

## Queries and projections

Use the smallest read path that matches the shape:

- A flat page with no lazy collection may return entities from a Spring Data repository and map them to a `dto/result` record in the service. Do not create a constructor projection just to avoid a short mapping method.
- A complex paper or aggregate read belongs in `query/`. Query classes are read-only and use the dependencies already present in this codebase: `JdbcClient` for aggregate/cross-module SQL and JPA `EntityManager` for hierarchical paper assembly.
- A query-side tree or row model that crosses into the service belongs in `dto/projection`, not in the query class. The service maps it to `dto/result`, resolves presigned URLs there, and returns no raw object keys to HTTP.
- For `Instant` parameters in `JdbcClient`, use `shared.persistence.SqlTime.at(...)` as the existing queries do.

```java
@Component
@RequiredArgsConstructor
public class ExamStatsQuery {

    private final JdbcClient jdbcClient;

    public List<LevelStat> countByLevel(Instant from) {
        return jdbcClient.sql("""
                select level, count(*) as total
                  from exam_attempts
                 where submitted_at >= :from
                 group by level
                """)
                .param("from", SqlTime.at(from))
                .query((rs, rowNum) -> new LevelStat(rs.getString("level"), rs.getLong("total")))
                .list();
    }
}
```

Keep SQL/JPQL in the query or repository that owns the read. There is no jOOQ/querydsl code or generated query metadata in this repository.

## Calling another module

Ask the owning module; never touch its tables.

```java
@Transactional(readOnly = true)
public List<AttemptResult> listAttempts(UUID examId) {
    List<ExamAttempt> attempts = attempts.findByExamId(examId);

    Set<UUID> learnerIds = attempts.stream()
            .map(ExamAttempt::getLearnerId)
            .collect(toSet());

    Map<UUID, LearnerSummary> learners = learnerService.findSummaries(learnerIds);  // one call

    return attempts.stream()
            .map(a -> AttemptResult.from(a, learners.get(a.getLearnerId())))
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

For AI jobs in this codebase, request-side modules depend on `ai.api.AiJobQueue` and call its capability-specific methods, such as `enqueueSpeechAssessment(...)` or `enqueueTutorReply(...)`. They must not construct or expose `AiJob`/`AiJobType`. Worker code uses the internal `ai.service.AiJobWorkerQueue`; handlers use `ai.api.AiJobHandler` and receive stable identifiers/payloads rather than persistence entities.

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
