# Architecture Rules

## Contents

1. Layering
2. Module ownership
3. Crossing module boundaries
4. Entities
5. Services and transactions
6. Persistence and schema
7. Read-only query code
8. Concurrency
9. Async work
10. Shared code
11. Determining the owning module

## Layering

`controller -> service -> repository`. Dependencies point one way only.

- **Controller** - HTTP only. Parse the request, call one service method, map to a response record. No business logic, no transaction, no repository access.
- **Service** - orchestration and the transaction boundary. Loads entities, calls their methods, coordinates with other modules' services, maps to records.
- **Repository** - Spring Data interfaces and projections. Nothing else.
- **Entity** - state plus the rules that protect it.

There is intentionally no separate domain model and no port/adapter tier. The entity is the model. Persistence ignorance is a Clean Architecture goal, not a requirement here, and the mapping layer it demands is a common source of subtle bugs around versions, detached instances, and child collections.

State this as a decision if asked, not as something unfinished.

## Module ownership

A module is a business capability, not a user role or a technical concern. Admin and end-user features for the same capability belong in the same module and differ only by controller.

Exactly one module may write a given table. Ownership is about writes; reads are governed separately below.

If a change would require two modules to write the same table, the design is wrong. Either one owns it and the other asks, or the table is really two tables. Raise it rather than working around it.

## Crossing module boundaries

Match the need to the mechanism:

**Needs an identifier only** - store a `UUID`. No JPA relationship across modules. A `@ManyToOne` across a boundary drags in lazy-loading chains, risks cascades reaching another module's data, and couples both schemas.

**Needs a few fields for display** - call the other module's service and receive a record it defines. Its entities do not leave it. Fetch the whole set of IDs in one call; never call per row inside a loop.

**Needs the other module to change something** - call its service. Never write to its tables.

**Needs a join for reporting** - a read-only query, declared as an exception. See below.

**Circular calls** are a design signal, not a technical problem. Check the direction first: usually only one module needs to know about the other. If both genuinely do, publish a Spring application event from one and listen in the other. If two modules can never be untangled, they are probably one module.

## Entities

Rules belong in the entity when an invalid business state is expressible. Otherwise the entity is plain data and the service holds the logic; that is a legitimate outcome, not a shortcut.

When an entity holds rules:

- Remove the setters for the fields those rules guard. A rule with a public setter beside it is not enforced.
- Do not annotate it with `@Data` or `@Setter`, which regenerate exactly those setters.
- Expose creation through a named static factory; keep the JPA no-arg constructor `protected`.
- Assign identity in code when the entity is created, rather than relying on database generation, so the object has identity from the moment it exists.
- Pass in plain values - `Instant now`, a `UUID` - never a repository, a service, or Spring-managed collaborators. An entity must not perform I/O.
- Do not declare a repository for an entity that is only ever reached through another entity.

These conventions reduce accidental rule bypass. They do not prevent deliberate bypass, and they do nothing about direct SQL writes. Anything that must never be violated needs a database constraint as well.

## Services and transactions

`@Transactional` goes on the service method. Not on the controller, not on the repository.

Keep transactions short. Do not call an external service, an AI provider, or object storage inside one - persist intent, commit, and let a worker continue.

`open-in-view` is false, so lazy associations are unavailable once the transaction ends. Services return records, never entities. Load everything the response needs while the transaction is open.

A service method should read as a sequence of intentions: load, act, coordinate, return. When it accumulates branching rules about one entity's state, those rules belong in that entity.

## Persistence and schema

Flyway is the only schema authority, and `ddl-auto` is `validate`. Hibernate never creates or alters anything.

- Never modify a migration that has already run - Flyway compares checksums and the application will refuse to start. Add a new versioned migration instead.
- Keep demo or seed data out of versioned migrations. Use a repeatable migration or a separate script so it does not become part of schema history.
- Every business-critical uniqueness rule gets a unique index, in addition to any application check.
- Index the foreign-key-like `UUID` columns used to look rows up; they carry no index by default.

## Read-only query code

Use the repository for lookups, projections, and ordinary paging. Reach for dedicated query code only when the repository genuinely cannot express it: optional filter combinations, aggregation and grouping, window functions, or joins across module boundaries.

That code lives in `query/`, separate from `repository/`, and is **read-only**. A second write path defeats table ownership and is easy to add by accident once a query DSL is available.

A query joining tables owned by different modules is an exception to the ownership rule. It is acceptable when it is declared: record in the module map which module hosts it, which schemas it reads, and what should happen when those schemas change. An exception that is written down and bounded is a design decision; the same one left unstated is a leak.

Generated query metadata is build output. It stays out of version control, and it must be regenerated whenever a migration changes - otherwise a stale reference fails at runtime rather than at compile time, which is the opposite of the point.

## Concurrency

Rules in an entity do not protect against two concurrent transactions. Each loads its own copy, each passes its checks, and both commit. Handle this explicitly:

- **`@Version`** on any entity whose updates could overlap. The later commit fails; translate that to HTTP 409 and let the client retry.
- **A database constraint** for anything that must never happen - a unique index for "only once per user", a check constraint for a value range. Application checks always leave a gap between read and write; a constraint does not.
- **Atomic updates** for counters and accumulators. A single `UPDATE ... SET x = x + 1` avoids the lost-update pattern that read-modify-write invites.
- **Idempotency** for anything a worker or an external callback may deliver twice: check current state before acting, and make repeat delivery harmless.

## Async work

Anything slow, external, or failure-prone runs outside the request: AI processing, media handling, notifications.

The shape is always the same:

1. Persist a record in a pending state and commit.
2. Enqueue after the commit, never before - an enqueue inside an uncommitted transaction can be consumed before the row exists.
3. A worker picks it up, marks progress, and writes the result.
4. Retries are bounded, failures are terminal and visible, and reprocessing the same item is harmless.

Never leave a job in a state where nothing will ever pick it up again. Timeouts and dead-letter handling are part of the feature, not an enhancement.

## Shared code

`shared` holds technical types only: base errors and error codes, paging types, a minimal current-user type. Nothing with business meaning.

Not in `shared`: entities used by several modules, business enums, DTOs passed between modules, or a base entity that has grown business fields. Each of those hides ownership and quietly breaks the boundary rules above.

Duplicate until the third occurrence before extracting. Two similar things are often coincidence. If `shared` grows past roughly ten files, something in it belongs to a module.

Reject `utils`, `helpers`, `manager`, `misc`, and cross-domain `CommonService` outright.

## Determining the owning module

Do not assume a fixed module list. Derive it from the repository: top-level business packages, the module map if present, existing migrations, and any architecture test already encoding boundaries.

1. Find which module already writes the tables involved. That module owns the change.
2. If several modules touch them, the writer owns it; the others read through its service or a declared query.
3. If none fits, say so and ask which module should own it rather than inventing one.

Creating a module is a long-lived ownership decision and belongs to a design conversation, not to an implementation task.
