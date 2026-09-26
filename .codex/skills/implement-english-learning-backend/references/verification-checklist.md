# Verification Checklist

Walk this before reporting a slice as finished. Anything unchecked is either fixed or reported.

## Placement

- The owning module is unambiguous, and it writes only tables it owns.
- Layers are respected: controller has no business logic and no repository access; repository holds no logic.
- Admin and end-user features sit in the same module, differing only by controller.
- `query/` and `worker/` exist only where actually used.
- Nothing landed in `shared` that carries business meaning.

## Rules and entities

- Rules that protect an invalid state live in the entity, not spread across services.
- Entities holding rules have no setters for the guarded fields, and no `@Data` or `@Setter`.
- Entities perform no I/O and receive plain values only.
- Plain CRUD stayed plain - no invented invariants.

## Boundaries

- Cross-module references are `UUID` fields; no `@ManyToOne` crosses a module.
- Other modules' data arrives through their service as a record, fetched for the whole ID set rather than per row.
- No module writes another module's tables.
- No new cycle between modules; if one was unavoidable, it goes through an event and is noted.
- Any cross-module read query is recorded as a declared exception.

## Transactions and persistence

- `@Transactional` is on the service, and no external call sits inside it.
- Everything the response needs is loaded inside the transaction - nothing relies on lazy access afterwards.
- Entities do not appear in any HTTP response.
- Schema changes are a new migration; no already-applied migration was edited.
- Seed or demo data stays out of versioned migrations.
- New lookup columns are indexed; uniqueness rules have a unique index.

## Concurrency

- Entities that can be updated concurrently carry `@Version`, and the conflict maps to 409.
- Anything that must never happen twice has a database constraint, not only an application check.
- Counters use atomic updates rather than read-modify-write.
- Async work is idempotent, bounded in retries, and cannot end in a state nothing will pick up.
- Enqueueing happens after commit.

## Async specifics

- The record is persisted and committed before the job is enqueued.
- Redelivery of the same job is harmless.
- Failures are terminal, visible, and time-bounded.

## Build and tests

- The project compiles and the tests pass. Do not report done without running them.
- Query metadata was regenerated if a migration changed.
- Entity rules are covered by tests that need no database.
- Failure paths are tested, not only the happy path.

## Report

State what changed, which rules applied, what was deliberately left out, and every assumption made. Flag anything that conflicts with the architecture rules rather than quietly working around it.
