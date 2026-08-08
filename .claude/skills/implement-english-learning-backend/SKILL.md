---
name: implement-english-learning-backend
description: Implement, extend, refactor, or review Java Spring Boot code for the English Learning Backend, a modular monolith built on a controller / service / repository layering with business rules kept in entities where invariants exist. Use this skill whenever work touches this codebase - adding or changing a feature, designing a REST endpoint, deciding package placement, writing entities and services, Spring Data repositories, read-only query code, Flyway migrations, async AI jobs, file storage, concurrency handling, or layer tests. Trigger it even when the request sounds like a plain coding task ("add an endpoint for X", "why is this query slow", "where should this class go") and names none of the above, because the placement and ownership rules still govern the answer.
---

# English Learning Backend

Java 21, Spring Boot, PostgreSQL, Flyway, Spring Data JPA. Read-only query code where a repository is not enough. Redis-backed workers for AI jobs. Object storage for audio and media.

The architecture is a three-layer modular monolith: `controller -> service -> repository`, with modules as the primary division and layers inside them. It is deliberately not hexagonal or Clean Architecture - there is no separate domain model, no port/adapter tier, and no persistence mapper. Business rules live in entities wherever an invalid state is possible; the rest is plain CRUD.

Implement one vertical slice at a time: one endpoint, its service, its persistence, its migration, its tests. Do not scaffold layers before they hold anything.

## Before writing code

Read the repository rather than assuming its shape:

1. Repository instructions - `CLAUDE.md`, `AGENTS.md`, or `README.md`, whichever exists.
2. `docs/module-map.md` if present. It records which module owns what and where rules live. Follow it.
3. The module you are about to touch, and at least one existing slice in it. Match its conventions over the examples here when they differ.
4. Existing migrations, so you know the real schema.

If no module clearly owns the change, say so and ask. Do not invent a module - that is a design decision, and there is a separate skill for it.

## Package placement

Modules first, layers inside:

```text
com.<app>/
├── Application.java
├── <module>/
│   ├── controller/       HTTP entry points
│   ├── service/          orchestration, @Transactional
│   ├── repository/       Spring Data interfaces, projections
│   ├── entity/           JPA entities, enums, module exceptions
│   ├── dto/              request/response records
│   ├── query/            read-only complex queries - only where needed
│   └── worker/           async job handling - only where needed
├── shared/               technical types only: error, page, security
└── config/               Spring configuration
```

Create `query/` and `worker/` only in modules that have them. Split `dto/` into `request/` and `response/` only once it grows past roughly ten files.

Admin and end-user features belong to the same module. They differ by controller and by use case, not by module - never mirror a module into `admin/` and `user/` trees.

## Classify the work first

Pick the shape before writing classes:

- **Command** - changes state. Controller -> service -> entity (if it has rules) -> repository. Transaction at the service.
- **Query** - reads only. Controller -> service -> repository projection, or `query/` when the repository is not enough. No entity behavior involved.
- **Simple CRUD** - no invariants. Plain service logic, plain entity. Do not manufacture rules that do not exist.
- **Async work** - anything slow or external. Persist a record, commit, then let a worker pick it up. Never do it inline in the request thread.

## Where business rules go

Put a rule in the entity when the entity has a state that is valid as data but wrong as business - a published exam with no questions, an attempt submitted twice, a streak continuing across a missed day.

When rules live in an entity, remove the setters for the fields they guard. An enforced rule with a public setter beside it is not enforced. Do not put `@Data` or `@Setter` on such an entity.

Keep in the service: anything spanning multiple entities, anything needing I/O, and anything with no rule to enforce. Entities receive plain values - `Instant now`, a `UUID` - never a repository, a service, or a `Clock`.

Details and examples: [implementation-patterns.md](references/implementation-patterns.md).

## Rules that always hold

- One module writes a given table. Others read through that module's service, or through a declared read-only query.
- Cross-module references are `UUID` fields. No `@ManyToOne` across module boundaries.
- Entities never leave their module and never appear in an HTTP response. Map to a record.
- `@Transactional` sits on the service method, never on a controller.
- Flyway owns the schema. `ddl-auto` is `validate`. Never edit a migration that has already run - add a new one.
- `open-in-view` is false. Anything the response needs must be loaded inside the transaction.
- Concurrency is handled explicitly: `@Version` for lost-update protection, a database constraint for anything that must never be violated.
- No `utils`, `helpers`, `common`, or `CommonService` packages.

Full reasoning and edge cases: [architecture-rules.md](references/architecture-rules.md).

## Plan the slice before writing it

State briefly:

1. The owning module and the tables involved.
2. The endpoint, its request and response records.
3. Whether any rule belongs in an entity, or whether this is plain CRUD.
4. What the service orchestrates, and where the transaction begins and ends.
5. Whether concurrent execution can break anything, and what prevents it.
6. Migration needed, if any.
7. Which tests are worth writing.

Raise any conflict with the rules above before coding, not after.

## Implementation order

1. Migration first, if the schema changes.
2. Entity and repository.
3. Service, including the rules that belong in the entity.
4. Controller and DTO records.
5. Tests.
6. Run the build and the tests. Do not report work as finished without them.

Write the smallest complete slice. Extend an existing class rather than adding a parallel one that does nearly the same thing.

## Before reporting done

Walk [verification-checklist.md](references/verification-checklist.md). Report what you changed, which rules applied, anything you deliberately left out, and anything you had to assume.
